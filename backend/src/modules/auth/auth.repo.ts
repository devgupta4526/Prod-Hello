import { timingSafeEqual } from 'node:crypto';
import { v7 as uuid } from 'uuid';
import type { Database } from '../../lib/database.js';
import { AppError } from '../../lib/app-error.js';
import { isAdult } from '../../lib/clock.js';
import type { ProfileInput } from './auth.schemas.js';

const includeSelf = { presence: true, profile: true, photos: true } as const;
export interface ChallengeInput { id: string; requestId: string; phoneE164: string; deviceId: string; ipHash: string; codeHash: string; sandbox: boolean; now: Date; dailyBudget: number }
export interface SessionInput { id: string; familyId: string; deviceId: string; refreshTokenHash: string; createdAt: Date; expiresAt: Date }

export class AuthRepository {
  constructor(private readonly db: Database) {}
  async reserveChallenge(input: ChallengeInput) {
    return this.db.$transaction(async (tx) => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(570020)`;
      const old = await tx.otpChallenge.findUnique({ where: { requestId: input.requestId } });
      if (old) {
        if (old.phoneE164 !== input.phoneE164 || old.deviceId !== input.deviceId) throw new AppError('conflict', 409);
        return { challenge: old, fresh: false };
      }
      const hour = new Date(input.now.getTime() - 3600000);
      const day = new Date(input.now.getTime() - 86400000);
      const midnight = new Date(input.now); midnight.setUTCHours(0, 0, 0, 0);
      const [phoneHour, phoneDay, ipHour, deviceDay, smsDay, last] = await Promise.all([
        tx.otpChallenge.count({ where: { phoneE164: input.phoneE164, createdAt: { gte: hour } } }),
        tx.otpChallenge.count({ where: { phoneE164: input.phoneE164, createdAt: { gte: day } } }),
        tx.otpChallenge.count({ where: { ipHash: input.ipHash, createdAt: { gte: hour } } }),
        tx.otpChallenge.count({ where: { deviceId: input.deviceId, createdAt: { gte: day } } }),
        tx.otpChallenge.count({ where: { sandbox: false, createdAt: { gte: midnight } } }),
        tx.otpChallenge.findFirst({ where: { phoneE164: input.phoneE164 }, orderBy: { createdAt: 'desc' } }),
      ]);
      if (phoneHour >= 5 || phoneDay >= 10 || ipHour >= 20 || deviceDay >= 10) throw new AppError('rate_limited', 429, 3600);
      if (!input.sandbox && smsDay >= input.dailyBudget) throw new AppError('sms_unavailable', 503);
      if (last) {
        const wait = Math.ceil((last.createdAt.getTime() + last.resendAfterSec * 1000 - input.now.getTime()) / 1000);
        if (wait > 0) throw new AppError('rate_limited', 429, wait);
        if (last.attempts >= 5 && last.createdAt > new Date(input.now.getTime() - 900000)) throw new AppError('rate_limited', 429, 900);
      }
      await tx.otpChallenge.updateMany({ where: { phoneE164: input.phoneE164, consumedAt: null }, data: { consumedAt: input.now } });
      const challenge = await tx.otpChallenge.create({ data: {
        id: input.id, requestId: input.requestId, phoneE164: input.phoneE164, deviceId: input.deviceId, ipHash: input.ipHash,
        codeHash: input.codeHash, sandbox: input.sandbox, createdAt: input.now, expiresAt: new Date(input.now.getTime() + 300000),
        resendAfterSec: phoneHour === 0 ? 30 : phoneHour === 1 ? 60 : 120,
        deliveryStatus: input.sandbox ? 'sent' : 'pending',
      } });
      return { challenge, fresh: true };
    });
  }
  async delivery(id: string, deliveryStatus: string, provider?: string, providerMessageId?: string) {
    await this.db.otpChallenge.update({ where: { id }, data: { deliveryStatus, ...(provider ? { provider } : {}), ...(providerMessageId ? { providerMessageId } : {}) } });
  }
  challenge(id: string) { return this.db.otpChallenge.findUnique({ where: { id } }); }
  async verifyChallenge(id: string, deviceId: string, hash: string, session: SessionInput, now: Date) {
    const outcome = await this.db.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "OtpChallenge" WHERE id = ${id}::uuid FOR UPDATE`;
      const challenge = await tx.otpChallenge.findUnique({ where: { id } });
      if (!challenge || challenge.deviceId !== deviceId) return { error: 'invalid_code' as const };
      if (challenge.consumedAt || challenge.expiresAt <= now) return { error: 'challenge_expired' as const };
      if (challenge.attempts >= 5) return { error: 'locked' as const };
      if (!['sent', 'delivered'].includes(challenge.deliveryStatus)) return { error: 'sms_unavailable' as const };
      const left = Buffer.from(challenge.codeHash, 'hex'); const right = Buffer.from(hash, 'hex');
      if (left.length !== right.length || !timingSafeEqual(left, right)) {
        await tx.otpChallenge.update({ where: { id }, data: { attempts: { increment: 1 } } });
        return { error: challenge.attempts >= 4 ? 'locked' as const : 'invalid_code' as const };
      }
      await tx.otpChallenge.update({ where: { id }, data: { consumedAt: now } });
      const existing = await tx.user.findUnique({ where: { phoneE164: challenge.phoneE164 } });
      if (existing?.status === 'DELETED') return { error: 'forbidden' as const };
      if (existing?.dateOfBirth && !isAdult(existing.dateOfBirth, now)) return { error: 'under_age' as const };
      const user = await tx.user.upsert({ where: { phoneE164: challenge.phoneE164 }, create: { id: uuid(), phoneE164: challenge.phoneE164, phoneVerifiedAt: now }, update: { phoneVerifiedAt: now }, include: includeSelf });
      // Recheck the locked/upserted row: DOB or account status may change after the initial lookup.
      if (user.status === 'DELETED') return { error: 'forbidden' as const };
      if (user.dateOfBirth && !isAdult(user.dateOfBirth, now)) return { error: 'under_age' as const };
      await tx.session.create({ data: { ...session, userId: user.id } });
      return { user, isNewUser: existing === null };
    });
    if ('error' in outcome) {
      const statuses = { invalid_code: 400, challenge_expired: 410, locked: 423, sms_unavailable: 503, forbidden: 403, under_age: 403 };
      throw new AppError(outcome.error, statuses[outcome.error]);
    }
    return outcome;
  }
  async rotate(hash: string, session: SessionInput, now: Date) {
    const outcome = await this.db.$transaction(async (tx) => {
      // Serialize rotation and reuse revocation so a concurrent descendant cannot escape revocation.
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(570021)`;
      await tx.$queryRaw`SELECT id FROM "Session" WHERE "refreshTokenHash" = ${hash} FOR UPDATE`;
      const old = await tx.session.findUnique({ where: { refreshTokenHash: hash }, include: { user: { include: includeSelf } } });
      if (!old) return null;
      if (old.revokedAt || old.replacedById || old.deviceId !== session.deviceId) {
        await tx.session.updateMany({ where: { familyId: old.familyId }, data: { revokedAt: now } });
        return null;
      }
      if (old.expiresAt <= now || old.user.status === 'DELETED' || (old.user.dateOfBirth && !isAdult(old.user.dateOfBirth, now))) return null;
      await tx.session.create({ data: { ...session, familyId: old.familyId, userId: old.userId } });
      await tx.session.update({ where: { id: old.id }, data: { revokedAt: now, replacedById: session.id } });
      return old.user;
    });
    if (!outcome) throw new AppError('unauthorized', 401);
    return outcome;
  }
  session(id: string) { return this.db.session.findUnique({ where: { id }, include: { user: { include: includeSelf } } }); }
  self(id: string) { return this.db.user.findUniqueOrThrow({ where: { id }, include: includeSelf }); }
  sessions(userId: string, now: Date) { return this.db.session.findMany({ where: { userId, revokedAt: null, expiresAt: { gt: now } }, select: { id: true, createdAt: true, expiresAt: true }, orderBy: { createdAt: 'desc' } }); }
  async revoke(userId: string, id: string, now: Date) {
    await this.db.$transaction(async (tx) => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(570021)`;
      const session = await tx.session.findFirst({ where: { id, userId } });
      if (session) await tx.session.updateMany({ where: { userId, familyId: session.familyId }, data: { revokedAt: now } });
    });
  }
  async dob(userId: string, dob: Date, now: Date) {
    return this.db.$transaction(async (tx) => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(570021)`;
      await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${userId}::uuid FOR UPDATE`;
      const user = await tx.user.findUniqueOrThrow({ where: { id: userId } });
      if (user.dateOfBirth && user.dateOfBirth.getTime() !== dob.getTime()) throw new AppError('conflict', 409);
      const adult = isAdult(dob, now);
      await tx.user.update({ where: { id: userId }, data: { dateOfBirth: dob, adultVerifiedAt: adult ? now : null } });
      if (!adult) await tx.session.updateMany({ where: { userId }, data: { revokedAt: now } });
      return adult;
    });
  }
  async profile(userId: string, input: ProfileInput) { await this.db.profile.upsert({ where: { userId }, create: { userId, ...input }, update: input }); }
  async acceptGuidelines(userId: string, now: Date) {
    return this.db.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${userId}::uuid FOR UPDATE`;
      const user = await tx.user.findUniqueOrThrow({ where: { id: userId }, include: includeSelf });
      if (!user.dateOfBirth || !isAdult(user.dateOfBirth, now) || !user.profile || user.photos.length === 0) throw new AppError('onboarding_incomplete', 409);
      return tx.user.update({ where: { id: userId }, data: { guidelinesAcceptedAt: now, status: 'ACTIVE' }, include: includeSelf });
    });
  }
  async createUpload(userId: string, id: string, storageKey: string, now: Date) {
    await this.db.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${userId}::uuid FOR UPDATE`;
      if (await tx.profilePhoto.count({ where: { userId } }) >= 6) throw new AppError('photo_limit', 409);
      const recent = await tx.photoUpload.count({ where: { userId, createdAt: { gt: new Date(now.getTime() - 3600000) } } });
      if (recent >= 12) throw new AppError('rate_limited', 429, 3600);
      await tx.photoUpload.create({ data: { id, userId, storageKey, createdAt: now } });
    });
  }
  upload(userId: string, id: string) { return this.db.photoUpload.findFirst({ where: { id, userId } }); }
  async removePhoto(userId: string, photoId: string, now: Date) {
    await this.db.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${userId}::uuid FOR UPDATE`;
      const photo = await tx.profilePhoto.findFirst({ where: { id: photoId, userId } });
      if (!photo) return;
      const user = await tx.user.findUniqueOrThrow({ where: { id: userId } });
      if (user.status === 'ACTIVE' && await tx.profilePhoto.count({ where: { userId } }) <= 1) throw new AppError('last_photo_required', 409);
      await tx.mediaDeletion.upsert({ where: { storageKey: photo.storageKey }, create: { id: uuid(), storageKey: photo.storageKey, nextAttemptAt: now }, update: {} });
      await tx.profilePhoto.delete({ where: { id: photo.id } });
    });
  }
  async confirmPhoto(userId: string, uploadId: string, url: string, now: Date) {
    await this.db.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "User" WHERE id = ${userId}::uuid FOR UPDATE`;
      const upload = await tx.photoUpload.findFirst({ where: { id: uploadId, userId } });
      if (!upload) throw new AppError('not_found', 404);
      if (upload.confirmedAt) return;
      const photos = await tx.profilePhoto.findMany({ where: { userId } });
      if (photos.length >= 6) throw new AppError('photo_limit', 409);
      const position = Array.from({ length: 6 }, (_, i) => i).find((i) => !photos.some((photo) => photo.position === i));
      if (position === undefined) throw new AppError('photo_limit', 409);
      await tx.profilePhoto.create({ data: { id: uuid(), userId, storageKey: upload.storageKey, url, position } });
      await tx.photoUpload.update({ where: { id: upload.id }, data: { confirmedAt: now } });
    });
  }
  async cleanup(now: Date) { await this.db.otpChallenge.deleteMany({ where: { createdAt: { lt: new Date(now.getTime() - 7 * 86400000) } } }); }
}
export type Self = Awaited<ReturnType<AuthRepository['self']>>;
