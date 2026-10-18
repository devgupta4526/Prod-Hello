import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { NotificationsRepository } from "./notifications.repo.js";
import { errorSchema } from "../system/system.schemas.js";

const errors = {
  400: errorSchema,
  401: errorSchema,
  403: errorSchema,
  404: errorSchema,
  429: errorSchema,
  503: errorSchema,
};

function identity(request: FastifyRequest) {
  if (!request.identity) throw new AppError("unauthorized", 401);
  return request.identity;
}

export function notificationsRoutes(
  app: FastifyInstance,
  repo: NotificationsRepository,
) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // GET /v1/notifications
  router.get(
    "/v1/notifications",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "listNotifications",
        querystring: z.object({
          cursor: z.string().optional(),
          limit: z.coerce.number().int().min(1).max(50).default(20),
        }),
        response: {
          200: z.object({
            items: z.array(
              z.object({
                id: z.string().uuid(),
                type: z.string(),
                payload: z.unknown(),
                readAt: z.string().nullable(),
                createdAt: z.string(),
              }),
            ),
            nextCursor: z.string().nullable(),
          }),
          ...errors,
        },
      },
    },
    async (req) =>
      repo.listNotifications(identity(req).user.id, req.query.cursor, req.query.limit),
  );

  // GET /v1/notifications/unread-count
  router.get(
    "/v1/notifications/unread-count",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getUnreadNotificationCount",
        response: {
          200: z.object({
            unreadCount: z.number(),
          }),
          ...errors,
        },
      },
    },
    async (req) => ({
      unreadCount: await repo.getUnreadCount(identity(req).user.id),
    }),
  );

  // POST /v1/notifications/read
  router.post(
    "/v1/notifications/read",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "markNotificationsRead",
        body: z.object({
          ids: z.array(z.string().uuid()).optional(),
          all: z.boolean().optional(),
        }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      await repo.markAsRead(identity(req).user.id, req.body.ids, req.body.all);
      return { ok: true as const };
    },
  );
}
