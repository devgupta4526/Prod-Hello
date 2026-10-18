import { execFileSync } from 'node:child_process';
import { PostgreSqlContainer } from '@testcontainers/postgresql';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { v7 as uuid } from 'uuid';
import { createDatabase, type Database } from '../src/lib/database.js';
import { AuthRepository } from '../src/modules/auth/auth.repo.js';
import { AuthService } from '../src/modules/auth/auth.service.js';
import { testEnvironment } from './environment.js';
import { parseEnvironment } from '../src/config/env.js';

let database: Database;
let service: AuthService;
let now = new Date('2026-10-17T00:00:00Z');
const cleanup: (() => Promise<unknown>)[] = [];
beforeAll(async () => {
  const container = await new PostgreSqlContainer('postgis/postgis:17-3.5').start();
  cleanup.unshift(() => container.stop());
  const url = container.getConnectionUri();
  execFileSync(process.execPath, ['node_modules/prisma/build/index.js', 'migrate', 'deploy'], { env: { ...process.env, DIRECT_URL: url }, stdio: 'pipe' });
  database = createDatabase(url);
  cleanup.unshift(() => database.$disconnect());
  service = new AuthService(new AuthRepository(database), parseEnvironment({ ...testEnvironment(url), OTP_SANDBOX_NUMBERS: '+919876543210:123456,+919876543211:123456,+919876543212:123456,+919876543213:123456,+919876543214:123456' }), { now: () => now });
}, 180000);
afterAll(async () => { for (const close of cleanup) await close(); });

describe('OTP and token transactions against PostgreSQL', () => {
  it('BR-02 retries the same request without issuing another challenge and consumes the code once', async () => {
    const deviceId = uuid(); const requestId = uuid();
    const input = { phone: '+919876543210', deviceId, requestId };
    const first = await service.request(input, '192.0.2.1');
    const retry = await service.request(input, '192.0.2.1');
    expect(retry.challengeId).toBe(first.challengeId);
    expect(await database.otpChallenge.count({ where: { requestId } })).toBe(1);
    const result = await service.verify({ challengeId: first.challengeId, deviceId, code: '123456' });
    expect(result.user.status).toBe('ONBOARDING');
    expect(result.user.hasDateOfBirth).toBe(false);
    await expect(service.verify({ challengeId: first.challengeId, deviceId, code: '123456' })).rejects.toMatchObject({ code: 'challenge_expired' });
  });
  it('BR-02 commits failed attempts and locks after five guesses', async () => {
    const deviceId = uuid();
    const challenge = await service.request({ phone: '+919876543211', deviceId, requestId: uuid() }, '192.0.2.2');
    for (let attempt = 1; attempt <= 5; attempt++) {
      await expect(service.verify({ challengeId: challenge.challengeId, deviceId, code: '000000' })).rejects.toMatchObject({ code: attempt === 5 ? 'locked' : 'invalid_code' });
    }
    expect((await database.otpChallenge.findUniqueOrThrow({ where: { id: challenge.challengeId } })).attempts).toBe(5);
    await expect(service.verify({ challengeId: challenge.challengeId, deviceId, code: '123456' })).rejects.toMatchObject({ code: 'locked' });
  });
  it('BR-02 rejects expired codes and binds verification to the requesting device', async () => {
    const deviceId = uuid();
    const challenge = await service.request({ phone: '+919876543212', deviceId, requestId: uuid() }, '192.0.2.3');
    await expect(service.verify({ challengeId: challenge.challengeId, deviceId: uuid(), code: '123456' })).rejects.toMatchObject({ code: 'invalid_code' });
    now = new Date(now.getTime() + 301000);
    await expect(service.verify({ challengeId: challenge.challengeId, deviceId, code: '123456' })).rejects.toMatchObject({ code: 'challenge_expired' });
  });
  it('BR-01 persists under-age DOB and revokes sessions without activating the account', async () => {
    const deviceId = uuid();
    const challenge = await service.request({ phone: '+919876543213', deviceId, requestId: uuid() }, '192.0.2.4');
    const tokens = await service.verify({ challengeId: challenge.challengeId, deviceId, code: '123456' });
    await expect(service.dob(tokens.user.id, '2010-01-01')).rejects.toMatchObject({ code: 'under_age' });
    expect((await database.user.findUniqueOrThrow({ where: { id: tokens.user.id } })).status).toBe('ONBOARDING');
    await expect(service.authenticate(`Bearer ${tokens.accessToken}`)).rejects.toMatchObject({ code: 'unauthorized' });
  });
  it('BR-02 refresh reuse revokes the entire token family, including the rotated session', async () => {
    const deviceId = uuid();
    const challenge = await service.request({ phone: '+919876543214', deviceId, requestId: uuid() }, '192.0.2.5');
    const original = await service.verify({ challengeId: challenge.challengeId, deviceId, code: '123456' });
    const rotated = await service.refresh(original.refreshToken, deviceId);
    await expect(service.refresh(original.refreshToken, deviceId)).rejects.toMatchObject({ code: 'unauthorized' });
    await expect(service.authenticate(`Bearer ${rotated.accessToken}`)).rejects.toMatchObject({ code: 'unauthorized' });
  });
});
