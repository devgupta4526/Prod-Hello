import { describe, expect, it } from 'vitest';
import { isAdult } from '../src/lib/clock.js';
import { profileSchema, requestOtpSchema, verifyOtpSchema } from '../src/modules/auth/auth.schemas.js';

describe('auth and onboarding contracts', () => {
  it('BR-01 computes the eighteenth birthday in UTC, not by year subtraction', () => {
    const dob = new Date('2008-10-17T00:00:00Z');
    expect(isAdult(dob, new Date('2026-10-16T23:59:59Z'))).toBe(false);
    expect(isAdult(dob, new Date('2026-10-17T00:00:00Z'))).toBe(true);
    expect(isAdult(new Date('2030-01-01T00:00:00Z'), new Date('2026-10-17T00:00:00Z'))).toBe(false);
  });
  it('BR-02 requires request and device identifiers and rejects extra identity fields', () => {
    expect(requestOtpSchema.safeParse({ phone: '+919876543210', deviceId: '0199aa00-0000-7000-8000-000000000001', requestId: '0199aa00-0000-7000-8000-000000000002', userId: 'attacker' }).success).toBe(false);
    expect(verifyOtpSchema.safeParse({ challengeId: '0199aa00-0000-7000-8000-000000000001', deviceId: '0199aa00-0000-7000-8000-000000000002', code: '123' }).success).toBe(false);
  });
  it('requires three distinct catalog interests and limits profile content', () => {
    expect(profileSchema.safeParse({ displayName: 'Aarav', bio: '', interests: ['Coffee', 'Coffee', 'Music'] }).success).toBe(false);
    expect(profileSchema.safeParse({ displayName: 'A', bio: '', interests: ['Coffee', 'Music', 'Books'] }).success).toBe(false);
    expect(profileSchema.safeParse({ displayName: 'Aarav', bio: 'Hello', interests: ['Coffee', 'Music', 'Books'] }).success).toBe(true);
  });
});
