import type { FastifyInstance } from 'fastify';
import type { ZodTypeProvider } from 'fastify-type-provider-zod';
import { validateRequest } from 'twilio/lib/webhooks/webhooks.js';
import { z } from 'zod';
import type { Environment } from '../../config/env.js';
import type { Database } from '../../lib/database.js';
import { AppError } from '../../lib/app-error.js';
import { errorSchema } from '../../modules/system/system.schemas.js';

export async function smsWebhooks(app: FastifyInstance, env: Environment, db: Database) {
  // Encapsulation confines form parsing to the provider route.
  await app.register(async (scope) => {
    scope.addContentTypeParser('application/x-www-form-urlencoded', { parseAs: 'string' }, (_request, body, done) => {
      const parameters = new URLSearchParams(String(body));
      const fields: Record<string, string> = {};
      for (const [key, value] of parameters) {
        if (Object.hasOwn(fields, key)) { done(new AppError('validation_failed', 400)); return; }
        Object.defineProperty(fields, key, { value, enumerable: true });
      }
      done(null, fields);
    });
    scope.withTypeProvider<ZodTypeProvider>().post('/v1/webhooks/sms/twilio', {
      config: { access: 'public', returnsPeople: false },
      schema: { operationId: 'twilioDeliveryReceipt', body: z.record(z.string(), z.string()), response: { 200: z.object({ ok: z.literal(true) }), 400: errorSchema, 403: errorSchema, 503: errorSchema } },
    }, async (request) => {
      const signature = request.headers['x-twilio-signature'];
      if (!env.TWILIO_AUTH_TOKEN || !env.TWILIO_STATUS_CALLBACK_URL || typeof signature !== 'string' ||
          !validateRequest(env.TWILIO_AUTH_TOKEN, signature, env.TWILIO_STATUS_CALLBACK_URL, request.body)) {
        throw new AppError('forbidden', 403);
      }
      const receipt = z.object({ MessageSid: z.string().regex(/^SM[0-9a-fA-F]{32}$/), MessageStatus: z.string() }).parse(request.body);
      const status = receipt.MessageStatus === 'delivered' ? 'delivered' : ['failed', 'undelivered'].includes(receipt.MessageStatus) ? 'failed' : null;
      if (status) {
        // Replays and late nonterminal events cannot downgrade a delivered message.
        await db.otpChallenge.updateMany({ where: { provider: 'twilio', providerMessageId: receipt.MessageSid, deliveryStatus: { not: 'delivered' } }, data: { deliveryStatus: status } });
      }
      return { ok: true as const };
    });
  });
}
