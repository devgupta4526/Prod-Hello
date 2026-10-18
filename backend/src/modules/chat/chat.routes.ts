import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { ChatService } from "./chat.service.js";
import {
  sendMessageSchema,
  messageItemSchema,
  conversationItemSchema,
  conversationDetailSchema,
  ackSchema,
  muteSchema,
} from "./chat.schemas.js";
import { errorSchema } from "../system/system.schemas.js";

const errors = {
  400: errorSchema,
  401: errorSchema,
  403: errorSchema,
  404: errorSchema,
  409: errorSchema,
  429: errorSchema,
  503: errorSchema,
};

function identity(request: FastifyRequest) {
  if (!request.identity) throw new AppError("unauthorized", 401);
  return request.identity;
}

export function chatRoutes(app: FastifyInstance, service: ChatService) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // GET /v1/conversations - List conversations
  router.get(
    "/v1/conversations",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listConversations",
        response: { 200: z.array(conversationItemSchema), ...errors },
      },
    },
    async (req) => service.listConversations(identity(req).user.id),
  );

  // GET /v1/conversations/:id - Conversation detail
  router.get(
    "/v1/conversations/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getConversationDetail",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: conversationDetailSchema, ...errors },
      },
    },
    async (req) => service.getConversationDetail(identity(req).user.id, req.params.id),
  );

  // GET /v1/conversations/:id/messages - Message history
  router.get(
    "/v1/conversations/:id/messages",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listMessages",
        params: z.object({ id: z.string().uuid() }),
        querystring: z.object({
          before: z.string().optional(),
          after: z.string().optional(),
          limit: z.coerce.number().int().min(1).max(100).default(50),
        }),
        response: { 200: z.array(messageItemSchema), ...errors },
      },
    },
    async (req) =>
      service.listMessages(req.params.id, identity(req).user.id, req.query),
  );

  // POST /v1/conversations/:id/messages - Send message
  router.post(
    "/v1/conversations/:id/messages",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "sendMessage",
        params: z.object({ id: z.string().uuid() }),
        body: sendMessageSchema,
        response: { 200: messageItemSchema, ...errors },
      },
    },
    async (req) =>
      service.sendMessage(req.params.id, identity(req).user.id, req.body),
  );

  // DELETE /v1/messages/:id - Tombstone message
  router.delete(
    "/v1/messages/:id",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "deleteMessage",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.deleteMessage(req.params.id, identity(req).user.id),
  );

  // POST /v1/conversations/:id/ack - Ack delivery
  router.post(
    "/v1/conversations/:id/ack",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "ackDelivery",
        params: z.object({ id: z.string().uuid() }),
        body: ackSchema,
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.ackDelivery(req.params.id, identity(req).user.id, req.body),
  );

  // PUT /v1/conversations/:id/mute - Mute conversation
  router.put(
    "/v1/conversations/:id/mute",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "muteConversation",
        params: z.object({ id: z.string().uuid() }),
        body: muteSchema,
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.setMute(req.params.id, identity(req).user.id, req.body),
  );
}
