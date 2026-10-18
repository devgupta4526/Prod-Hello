import { createHmac, randomBytes, randomInt } from 'node:crypto';
import { jwtVerify, SignJWT } from 'jose';
import { parsePhoneNumberFromString } from 'libphonenumber-js';
import { v7 as uuid } from 'uuid';
import type { Environment } from '../../config/env.js';
import { AppError } from '../../lib/app-error.js';
import type { Clock } from '../../lib/clock.js';
import { isAdult } from '../../lib/clock.js';
import { FailoverSender } from '../../providers/sms/sender.js';
import { CloudinaryStorage } from '../../providers/storage/cloudinary.js';
import { AuthRepository, type Self } from './auth.repo.js';
import type { OtpRequest, OtpVerify, ProfileInput } from './auth.schemas.js';

export function publicSelf(user: Self) {
  return { id: user.id, status: user.status, hasDateOfBirth: user.dateOfBirth !== null, hasProfile: user.profile !== null, hasPhoto: user.photos.length > 0, guidelinesAccepted: user.guidelinesAcceptedAt !== null };
}
export class AuthService {
  private readonly sender: FailoverSender;
  private readonly storage: CloudinaryStorage;
  constructor(private readonly repo: AuthRepository, private readonly env: Environment, private readonly clock: Clock) {
    this.sender = new FailoverSender(env); this.storage = new CloudinaryStorage(env);
  }
  private hash(value: string, pepper: string) { return createHmac('sha256', pepper).update(value).digest('hex'); }
  async request(input: OtpRequest, ip: string) {
    const phone = parsePhoneNumberFromString(input.phone);
    if (!phone?.isValid()) throw new AppError('invalid_phone', 400);
    if (!phone.country || !this.env.OTP_ALLOWED_COUNTRIES.split(',').includes(phone.country)) throw new AppError('country_not_supported', 403);
    const sandbox = new Map(this.env.OTP_SANDBOX_NUMBERS.split(',').filter(Boolean).map((entry) => { const [number, code] = entry.split(':'); return [number, code]; }));
    const fixed = sandbox.get(phone.number);
    const code = fixed ?? randomInt(0, 1000000).toString().padStart(6, '0');
    const id = uuid(); const now = this.clock.now();
    const { challenge, fresh } = await this.repo.reserveChallenge({ id, requestId: input.requestId, phoneE164: phone.number, deviceId: input.deviceId, ipHash: this.hash(ip, this.env.OTP_HASH_PEPPER), codeHash: this.hash(`${code}|${phone.number}|${id}`, this.env.OTP_HASH_PEPPER), sandbox: fixed !== undefined, now, dailyBudget: this.env.OTP_DAILY_SMS_BUDGET });
    if (fresh && fixed === undefined) {
      try {
        const result = await this.sender.send({ toE164: phone.number, code, requestId: input.requestId, ...(input.appHash ? { appHash: input.appHash } : {}) });
        await this.repo.delivery(id, 'sent', result.provider, result.providerMessageId);
      } catch {
        await this.repo.delivery(id, 'failed'); throw new AppError('sms_unavailable', 503);
      }
    } else if (!['sent', 'delivered'].includes(challenge.deliveryStatus)) throw new AppError('sms_unavailable', 503);
    if (challenge.expiresAt <= now || challenge.consumedAt) throw new AppError('challenge_expired', 410);
    return { challengeId: challenge.id, resendAfterSec: Math.max(0, Math.ceil((challenge.createdAt.getTime() + challenge.resendAfterSec * 1000 - now.getTime()) / 1000)), expiresInSec: Math.max(0, Math.floor((challenge.expiresAt.getTime() - now.getTime()) / 1000)) };
  }
  private newSession(deviceId: string) {
    const now = this.clock.now(); const refreshToken = randomBytes(48).toString('base64url');
    return { refreshToken, session: { id: uuid(), familyId: uuid(), deviceId, refreshTokenHash: this.hash(refreshToken, this.env.JWT_REFRESH_PEPPER), createdAt: now, expiresAt: new Date(now.getTime() + 30 * 86400000) } };
  }
  private async token(user: Self, sessionId: string) {
    const now = Math.floor(this.clock.now().getTime() / 1000);
    return new SignJWT({ sid: sessionId }).setProtectedHeader({ alg: 'HS256' }).setSubject(user.id).setIssuer('hello-api').setAudience('hello-android').setIssuedAt(now).setExpirationTime(now + 900).sign(new TextEncoder().encode(this.env.JWT_ACCESS_SECRET));
  }
  async verify(input: OtpVerify) {
    const challenge = await this.repo.challenge(input.challengeId);
    if (!challenge) throw new AppError('invalid_code', 400);
    const { refreshToken, session } = this.newSession(input.deviceId);
    const result = await this.repo.verifyChallenge(input.challengeId, input.deviceId, this.hash(`${input.code}|${challenge.phoneE164}|${input.challengeId}`, this.env.OTP_HASH_PEPPER), session, this.clock.now());
    return { accessToken: await this.token(result.user, session.id), refreshToken, isNewUser: result.isNewUser, user: publicSelf(result.user) };
  }
  async refresh(refresh: string, deviceId: string) {
    const { refreshToken, session } = this.newSession(deviceId);
    const user = await this.repo.rotate(this.hash(refresh, this.env.JWT_REFRESH_PEPPER), session, this.clock.now());
    return { accessToken: await this.token(user, session.id), refreshToken, isNewUser: false, user: publicSelf(user) };
  }
  async authenticate(authorization?: string) {
    if (!authorization?.startsWith('Bearer ')) throw new AppError('unauthorized', 401);
    let payload;
    try { ({ payload } = await jwtVerify(authorization.slice(7), new TextEncoder().encode(this.env.JWT_ACCESS_SECRET), { algorithms: ['HS256'], issuer: 'hello-api', audience: 'hello-android', currentDate: this.clock.now() })); }
    catch { throw new AppError('unauthorized', 401); }
    if (typeof payload.sid !== 'string') throw new AppError('unauthorized', 401);
    const session = await this.repo.session(payload.sid);
    if (!session || session.userId !== payload.sub || session.revokedAt || session.expiresAt <= this.clock.now() || session.user.status === 'DELETED') throw new AppError('unauthorized', 401);
    if (session.user.dateOfBirth && !isAdult(session.user.dateOfBirth, this.clock.now())) throw new AppError('under_age', 403);
    return { sessionId: session.id, user: session.user };
  }
  async self(userId: string) { return publicSelf(await this.repo.self(userId)); }
  async sessions(userId: string, current: string) { return (await this.repo.sessions(userId, this.clock.now())).map((s) => ({ ...s, createdAt: s.createdAt.toISOString(), expiresAt: s.expiresAt.toISOString(), current: s.id === current })); }
  async revoke(userId: string, sessionId: string) { await this.repo.revoke(userId, sessionId, this.clock.now()); return { ok: true as const }; }
  async dob(userId: string, date: string) {
    const dob = new Date(`${date}T00:00:00Z`);
    if (!Number.isFinite(dob.getTime()) || dob.toISOString().slice(0, 10) !== date || dob > this.clock.now()) throw new AppError('validation_failed', 400);
    if (!await this.repo.dob(userId, dob, this.clock.now())) throw new AppError('under_age', 403);
    return this.self(userId);
  }
  async profile(userId: string, input: ProfileInput) { await this.repo.profile(userId, input); return this.self(userId); }
  async ownProfile(userId: string) {
    const user = await this.repo.self(userId);
    return {
      userId: user.id,
      availability: user.presence && user.presence.expiresAt > this.clock.now() ? user.presence.availability : "OFF",
      age: null,
      profile: user.profile ? { displayName: user.profile.displayName, bio: user.profile.bio ?? '', interests: user.profile.interests as any } : null,
      photos: user.photos.sort((a, b) => a.position - b.position).map(({ id, url, position }) => ({ id, url, position })),
    };
  }
  async removePhoto(userId: string, photoId: string) {
    await this.repo.removePhoto(userId, photoId, this.clock.now());
    return this.ownProfile(userId);
  }
  async guidelines(userId: string) { return publicSelf(await this.repo.acceptGuidelines(userId, this.clock.now())); }
  async upload(userId: string) {
    const id = uuid(); const key = `hello/profiles/${userId}/${id}`; const now = this.clock.now();
    const signed = this.storage.sign(key, now);
    await this.repo.createUpload(userId, id, key, now);
    return { uploadId: id, ...signed };
  }
  async confirmPhoto(userId: string, uploadId: string) {
    const upload = await this.repo.upload(userId, uploadId);
    if (!upload || upload.createdAt.getTime() + 3600000 < this.clock.now().getTime()) throw new AppError('not_found', 404);
    await this.repo.confirmPhoto(userId, uploadId, await this.storage.verify(upload.storageKey), this.clock.now());
    return this.self(userId);
  }
}
export type Identity = Awaited<ReturnType<AuthService['authenticate']>>;
