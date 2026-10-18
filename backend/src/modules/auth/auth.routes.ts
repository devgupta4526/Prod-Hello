import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { ZodTypeProvider } from 'fastify-type-provider-zod';
import { z } from 'zod';
import { AppError } from '../../lib/app-error.js';
import type { AuthService } from './auth.service.js';
import { dobSchema, otpResponseSchema, profileSchema, publicSelfSchema, refreshSchema, requestOtpSchema, sessionSchema, successSchema, tokenResponseSchema, verifyOtpSchema } from './auth.schemas.js';
import { errorSchema } from '../system/system.schemas.js';
import { ownProfileSchema } from './auth.schemas.js';
const errors = { 400: errorSchema, 401: errorSchema, 403: errorSchema, 404: errorSchema, 409: errorSchema, 410: errorSchema, 423: errorSchema, 429: errorSchema, 503: errorSchema };
function identity(request: FastifyRequest) { if (!request.identity) throw new AppError('unauthorized', 401); return request.identity; }
export function authRoutes(app: FastifyInstance, service: AuthService) {
  const router = app.withTypeProvider<ZodTypeProvider>();
  const publicRoute = { access: 'public' as const, returnsPeople: false };
  const sessionRoute = { access: 'session' as const, returnsPeople: false };
  const onboardingRoute = { access: 'onboarding' as const, returnsPeople: false };
  router.post('/v1/auth/otp/request', { config: publicRoute, schema: { operationId: 'requestOtp', body: requestOtpSchema, response: { 202: otpResponseSchema, ...errors } } }, async (req, reply) => reply.code(202).send(await service.request(req.body, req.ip)));
  router.post('/v1/auth/otp/verify', { config: publicRoute, schema: { operationId: 'verifyOtp', body: verifyOtpSchema, response: { 200: tokenResponseSchema, ...errors } } }, async (req) => service.verify(req.body));
  router.post('/v1/auth/refresh', { config: publicRoute, schema: { operationId: 'refreshSession', body: refreshSchema, response: { 200: tokenResponseSchema, ...errors } } }, async (req) => service.refresh(req.body.refreshToken, req.body.deviceId));
  router.post('/v1/auth/logout', { config: sessionRoute, schema: { operationId: 'logout', response: { 200: successSchema, ...errors } } }, async (req) => { const session = identity(req); return service.revoke(session.user.id, session.sessionId); });
  router.get('/v1/auth/sessions', { config: sessionRoute, schema: { operationId: 'listSessions', response: { 200: z.array(sessionSchema), ...errors } } }, async (req) => { const session = identity(req); return service.sessions(session.user.id, session.sessionId); });
  router.delete('/v1/auth/sessions/:id', { config: sessionRoute, schema: { operationId: 'revokeSession', params: z.object({ id: z.uuid() }), response: { 200: successSchema, ...errors } } }, async (req) => service.revoke(identity(req).user.id, req.params.id));
  router.get('/v1/me', { config: sessionRoute, schema: { operationId: 'getSelf', response: { 200: publicSelfSchema, ...errors } } }, async (req) => service.self(identity(req).user.id));
  router.put('/v1/me/dob', { config: onboardingRoute, schema: { operationId: 'setDateOfBirth', body: dobSchema, response: { 200: publicSelfSchema, ...errors } } }, async (req) => service.dob(identity(req).user.id, req.body.dateOfBirth));
  router.patch('/v1/me/profile', { config: onboardingRoute, schema: { operationId: 'setProfile', body: profileSchema, response: { 200: publicSelfSchema, ...errors } } }, async (req) => service.profile(identity(req).user.id, req.body));
  router.get('/v1/me/profile', { config: onboardingRoute, schema: { operationId: 'getOwnProfile', response: { 200: ownProfileSchema, ...errors } } }, async (req) => service.ownProfile(identity(req).user.id));
  router.delete('/v1/me/photos/:id', { config: onboardingRoute, schema: { operationId: 'removeOwnPhoto', params: z.object({ id: z.uuid() }), response: { 200: ownProfileSchema, ...errors } } }, async (req) => service.removePhoto(identity(req).user.id, req.params.id));
  router.post('/v1/me/guidelines/accept', { config: onboardingRoute, schema: { operationId: 'acceptGuidelines', response: { 200: publicSelfSchema, ...errors } } }, async (req) => service.guidelines(identity(req).user.id));
  router.post('/v1/me/photos/upload-url', { config: onboardingRoute, schema: { operationId: 'signPhotoUpload', response: { 200: z.object({ uploadId: z.uuid(), uploadUrl: z.url(), fields: z.record(z.string(), z.string()) }), ...errors } } }, async (req) => service.upload(identity(req).user.id));
  router.post('/v1/me/photos', { config: onboardingRoute, schema: { operationId: 'confirmPhoto', body: z.object({ uploadId: z.uuid() }).strict(), response: { 200: publicSelfSchema, ...errors } } }, async (req) => service.confirmPhoto(identity(req).user.id, req.body.uploadId));
}
