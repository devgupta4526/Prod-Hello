import { randomUUID } from "node:crypto";
import { v7 as uuid } from "uuid";
import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { SafetyRepository } from "./safety.repo.js";
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

function requireStaff(request: FastifyRequest) {
  const user = identity(request).user;
  if (user.role !== "MODERATOR" && user.role !== "ADMIN") {
    throw new AppError("forbidden", 403);
  }
  return user;
}

export function safetyRoutes(
  app: FastifyInstance,
  repo: SafetyRepository,
  db: Database,
  clock: Clock,
) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // POST /v1/blocks - Block user (BR-70 immediate)
  router.post(
    "/v1/blocks",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "blockUser",
        body: z.object({ userId: z.string().uuid() }).strict(),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      await repo.blockUser(identity(req).user.id, req.body.userId, clock.now());
      return { ok: true as const };
    },
  );

  // DELETE /v1/blocks/:userId - Unblock user
  router.delete(
    "/v1/blocks/:userId",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "unblockUser",
        params: z.object({ userId: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      await repo.unblockUser(identity(req).user.id, req.params.userId);
      return { ok: true as const };
    },
  );

  // GET /v1/blocks - List blocked users
  router.get(
    "/v1/blocks",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listBlockedUsers",
        response: {
          200: z.array(
            z.object({
              userId: z.string().uuid(),
              displayName: z.string(),
              photoUrl: z.string().nullable(),
              blockedAt: z.string(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => repo.listBlockedUsers(identity(req).user.id),
  );

  // POST /v1/reports - Report content/user
  router.post(
    "/v1/reports",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "createReport",
        body: z.object({
          targetType: z.string().min(1),
          targetId: z.string().min(1),
          targetUserId: z.string().uuid().optional(),
          reason: z.string().min(2).max(100),
          details: z.string().max(1000).optional(),
          evidence: z.record(z.string(), z.unknown()).optional(),
        }),
        response: {
          201: z.object({ id: z.string().uuid(), status: z.string() }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const report = await repo.createReport({
        reporterId: identity(req).user.id,
        targetType: req.body.targetType,
        targetId: req.body.targetId,
        targetUserId: req.body.targetUserId,
        reason: req.body.reason,
        details: req.body.details,
        evidence: req.body.evidence,
        now: clock.now(),
      });
      return reply.code(201).send({ id: report.id, status: report.status });
    },
  );

  // GET /v1/admin/reports - Moderator queue
  router.get(
    "/v1/admin/reports",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listAdminReports",
        response: {
          200: z.array(
            z.object({
              id: z.string().uuid(),
              reporterId: z.string().uuid(),
              targetType: z.string(),
              targetId: z.string(),
              reason: z.string(),
              status: z.string(),
              createdAt: z.string(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => {
      requireStaff(req);
      const reports = await db.report.findMany({
        orderBy: { createdAt: "desc" },
        take: 50,
      });
      return reports.map((r) => ({
        id: r.id,
        reporterId: r.reporterId,
        targetType: r.targetType,
        targetId: r.targetId,
        reason: r.reason,
        status: r.status,
        createdAt: r.createdAt.toISOString(),
      }));
    },
  );

  // POST /v1/admin/users/:id/suspend
  router.post(
    "/v1/admin/users/:id/suspend",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "suspendUser",
        params: z.object({ id: z.string().uuid() }),
        body: z.object({
          reason: z.string().min(2),
          until: z.string().datetime().optional(),
        }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const staff = requireStaff(req);
      const now = clock.now();
      const suspendedUntil = req.body.until ? new Date(req.body.until) : null;

      await db.user.update({
        where: { id: req.params.id },
        data: {
          status: "SUSPENDED",
          suspendedUntil,
        },
      });

      await db.moderationAction.create({
        data: {
          id: uuid(),
          targetUserId: req.params.id,
          actorId: staff.id,
          action: "SUSPEND",
          reason: req.body.reason,
          expiresAt: suspendedUntil,
          createdAt: now,
        },
      });

      await repo.createAuditLog({
        actorId: staff.id,
        action: "USER_SUSPENDED",
        entityType: "User",
        entityId: req.params.id,
        meta: { reason: req.body.reason, until: suspendedUntil },
        now,
      });

      return { ok: true as const };
    },
  );

  // POST /v1/admin/users/:id/unsuspend
  router.post(
    "/v1/admin/users/:id/unsuspend",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "unsuspendUser",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const staff = requireStaff(req);
      const now = clock.now();

      await db.user.update({
        where: { id: req.params.id },
        data: {
          status: "ACTIVE",
          suspendedUntil: null,
        },
      });

      await repo.createAuditLog({
        actorId: staff.id,
        action: "USER_UNSUSPENDED",
        entityType: "User",
        entityId: req.params.id,
        now,
      });

      return { ok: true as const };
    },
  );

  // GET /v1/admin/organizer-applications
  router.get(
    "/v1/admin/organizer-applications",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listAdminOrganizerApplications",
        response: {
          200: z.array(
            z.object({
              id: z.string().uuid(),
              userId: z.string().uuid(),
              orgName: z.string(),
              description: z.string(),
              status: z.string(),
              createdAt: z.string(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => {
      requireStaff(req);
      const apps = await db.organizerApplication.findMany({
        where: { status: "PENDING" },
        orderBy: { createdAt: "asc" },
      });
      return apps.map((a) => ({
        id: a.id,
        userId: a.userId,
        orgName: a.orgName,
        description: a.description,
        status: a.status,
        createdAt: a.createdAt.toISOString(),
      }));
    },
  );

  // POST /v1/admin/organizer-applications/:id/decide
  router.post(
    "/v1/admin/organizer-applications/:id/decide",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "decideOrganizerApplication",
        params: z.object({ id: z.string().uuid() }),
        body: z.object({ approved: z.boolean() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const staff = requireStaff(req);
      const now = clock.now();

      const appRecord = await db.organizerApplication.findUnique({
        where: { id: req.params.id },
      });
      if (!appRecord) throw new AppError("not_found", 404);

      const status = req.body.approved ? "APPROVED" : "REJECTED";
      await db.organizerApplication.update({
        where: { id: req.params.id },
        data: {
          status,
          reviewedBy: staff.id,
          reviewedAt: now,
        },
      });

      if (req.body.approved) {
        await db.user.update({
          where: { id: appRecord.userId },
          data: { isOrganizer: true },
        });
      }

      await repo.createAuditLog({
        actorId: staff.id,
        action: `ORGANIZER_APP_${status}`,
        entityType: "OrganizerApplication",
        entityId: appRecord.id,
        now,
      });

      return { ok: true as const };
    },
  );
}
