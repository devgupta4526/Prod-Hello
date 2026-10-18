import { z } from 'zod';
export const requestOtpSchema = z.object({ phone: z.string().max(20), deviceId: z.uuid(), requestId: z.uuid(), appHash: z.string().regex(/^[A-Za-z0-9+/]{11}$/).optional() }).strict();
export const verifyOtpSchema = z.object({ challengeId: z.uuid(), deviceId: z.uuid(), code: z.string().regex(/^\d{6}$/) }).strict();
export const refreshSchema = z.object({ refreshToken: z.string().min(40).max(200), deviceId: z.uuid() }).strict();
export const otpResponseSchema = z.object({ challengeId: z.uuid(), resendAfterSec: z.number().int(), expiresInSec: z.number().int() });
export const publicSelfSchema = z.object({ id: z.uuid(), status: z.enum(['ONBOARDING', 'ACTIVE', 'SUSPENDED', 'DELETION_PENDING', 'DELETED']), hasDateOfBirth: z.boolean(), hasProfile: z.boolean(), hasPhoto: z.boolean(), guidelinesAccepted: z.boolean() });
export const tokenResponseSchema = z.object({ accessToken: z.string(), refreshToken: z.string(), isNewUser: z.boolean(), user: publicSelfSchema });
export const sessionSchema = z.object({ id: z.uuid(), createdAt: z.iso.datetime(), expiresAt: z.iso.datetime(), current: z.boolean() });
export const successSchema = z.object({ ok: z.literal(true) });
export const interests = ['Coffee', 'Music', 'Food', 'Books', 'Art', 'Travel', 'Fitness', 'Outdoors', 'Gaming', 'Photography', 'Sports', 'Technology'] as const;
export const profileSchema = z.object({ displayName: z.string().trim().min(2).max(30), bio: z.string().trim().max(160), interests: z.array(z.enum(interests)).min(3).max(8).refine((items) => new Set(items).size === items.length) }).strict();
export const dobSchema = z.object({ dateOfBirth: z.iso.date() }).strict();
export const ownProfileSchema = z.object({
  userId: z.uuid(),
  availability: z.string().nullable(),
  age: z.number().nullable(),
  profile: profileSchema.nullable(),
  photos: z.array(z.object({ id: z.uuid(), url: z.url(), position: z.number().int().min(0).max(5) })).max(6),
});
export type OtpRequest = z.infer<typeof requestOtpSchema>;
export type OtpVerify = z.infer<typeof verifyOtpSchema>;
export type ProfileInput = z.infer<typeof profileSchema>;
