import { v7 as uuid } from "uuid";
import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { Database } from "../../lib/database.js";
import type { Clock } from "../../lib/clock.js";
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

export function meRoutes(app: FastifyInstance, db: Database, clock: Clock) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // PUT /v1/me/settings
  router.put(
    "/v1/me/settings",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "updateSettings",
        body: z.object({
          discoveryRadiusKm: z.number().int().min(1).max(25).optional(),
          showAge: z.boolean().optional(),
        }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      const updateData: Record<string, unknown> = {};
      if (req.body.discoveryRadiusKm !== undefined)
        updateData.discoveryRadiusKm = req.body.discoveryRadiusKm;
      if (req.body.showAge !== undefined) updateData.showAge = req.body.showAge;
      await db.profile.update({
        where: { userId },
        data: updateData,
      });
      return { ok: true as const };
    },
  );

  // PUT /v1/me/devices - Register FCM token
  router.put(
    "/v1/me/devices",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "registerDevice",
        body: z.object({
          deviceId: z.string().min(1),
          fcmToken: z.string().optional(),
          platform: z.enum(["android", "ios"]).default("android"),
          appVersion: z.string().default("1.0.0"),
        }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      const now = clock.now();
      await db.device.upsert({
        where: {
          userId_deviceId: { userId, deviceId: req.body.deviceId },
        },
        create: {
          id: uuid(),
          userId,
          deviceId: req.body.deviceId,
          fcmToken: req.body.fcmToken ?? null,
          platform: req.body.platform,
          appVersion: req.body.appVersion,
          lastSeenAt: now,
        },
        update: {
          fcmToken: req.body.fcmToken ?? null,
          appVersion: req.body.appVersion,
          lastSeenAt: now,
        },
      });
      return { ok: true as const };
    },
  );

  // DELETE /v1/me/devices/:deviceId - Unregister device
  router.delete(
    "/v1/me/devices/:deviceId",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "unregisterDevice",
        params: z.object({ deviceId: z.string() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      await db.device.deleteMany({
        where: { userId, deviceId: req.params.deviceId },
      });
      return { ok: true as const };
    },
  );

  // GET /v1/me/notification-preferences
  router.get(
    "/v1/me/notification-preferences",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getNotificationPreferences",
        response: {
          200: z.array(
            z.object({
              category: z.string(),
              push: z.boolean(),
              inApp: z.boolean(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      return db.notificationPreference.findMany({ where: { userId } });
    },
  );

  // PUT /v1/me/notification-preferences
  router.put(
    "/v1/me/notification-preferences",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "updateNotificationPreference",
        body: z.object({
          category: z.enum(["hellos", "messages", "pools", "tickets", "safety"]),
          push: z.boolean(),
          inApp: z.boolean(),
        }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      // Safety notices cannot be disabled (BR-61)
      const push = req.body.category === "safety" ? true : req.body.push;
      const inApp = req.body.category === "safety" ? true : req.body.inApp;

      await db.notificationPreference.upsert({
        where: {
          userId_category: { userId, category: req.body.category },
        },
        create: {
          userId,
          category: req.body.category,
          push,
          inApp,
        },
        update: { push, inApp },
      });
      return { ok: true as const };
    },
  );

  // POST /v1/me/data-export - Request account data export
  router.post(
    "/v1/me/data-export",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "requestDataExport",
        response: {
          202: z.object({ id: z.string().uuid(), status: z.string() }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const userId = identity(req).user.id;
      const exportRecord = await db.dataExport.create({
        data: {
          id: uuid(),
          userId,
          status: "PENDING",
          expiresAt: new Date(clock.now().getTime() + 7 * 86400000),
          createdAt: clock.now(),
        },
      });
      return reply.code(202).send({ id: exportRecord.id, status: exportRecord.status });
    },
  );

  // POST /v1/me/delete - Request account deletion (30-day grace period)
  router.post(
    "/v1/me/delete",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "requestAccountDeletion",
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      await db.user.update({
        where: { id: userId },
        data: {
          status: "DELETION_PENDING",
          deletionRequestedAt: clock.now(),
        },
      });
      // Delete active presence
      await db.presence.deleteMany({ where: { userId } });
      return { ok: true as const };
    },
  );

  // POST /v1/me/delete/cancel - Cancel pending deletion
  router.post(
    "/v1/me/delete/cancel",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "cancelAccountDeletion",
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const userId = identity(req).user.id;
      await db.user.update({
        where: { id: userId },
        data: {
          status: "ACTIVE",
          deletionRequestedAt: null,
        },
      });
      return { ok: true as const };
    },
  );

  // POST /v1/me/appeals - Submit appeal while suspended
  router.post(
    "/v1/me/appeals",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "submitSuspensionAppeal",
        body: z.object({
          message: z.string().trim().min(10).max(1000),
        }),
        response: {
          201: z.object({ id: z.string().uuid(), status: z.string() }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const userId = identity(req).user.id;
      const latestAction = await db.moderationAction.findFirst({
        where: { targetUserId: userId },
        orderBy: { createdAt: "desc" },
      });

      const appeal = await db.appeal.create({
        data: {
          id: uuid(),
          userId,
          moderationActionId: latestAction?.id ?? uuid(),
          message: req.body.message,
          status: "PENDING",
          createdAt: clock.now(),
        },
      });

      return reply.code(201).send({ id: appeal.id, status: appeal.status });
    },
  );
}
