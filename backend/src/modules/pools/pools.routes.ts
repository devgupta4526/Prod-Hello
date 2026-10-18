import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { PoolsService } from "./pools.service.js";
import {
  createPoolSchema,
  updatePoolSchema,
  knockSchema,
  poolItemSchema,
  poolDetailSchema,
  poolMemberSchema,
  poolKnockItemSchema,
} from "./pools.schemas.js";
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

export function poolsRoutes(app: FastifyInstance, service: PoolsService) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // GET /v1/pools/nearby - List nearby pools
  router.get(
    "/v1/pools/nearby",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listNearbyPools",
        querystring: z.object({
          category: z.string().optional(),
        }),
        response: { 200: z.array(poolItemSchema), ...errors },
      },
    },
    async (req) => service.listNearbyPools(identity(req).user.id, req.query.category),
  );

  // POST /v1/pools - Create pool
  router.post(
    "/v1/pools",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "createPool",
        body: createPoolSchema,
        response: { 201: poolDetailSchema, ...errors },
      },
    },
    async (req, reply) => {
      const pool = await service.createPool(identity(req).user.id, req.body);
      return reply.code(201).send(pool);
    },
  );

  // GET /v1/pools/:id - Detail of pool (BR-45 exact venue for members only)
  router.get(
    "/v1/pools/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getPoolDetail",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: poolDetailSchema, ...errors },
      },
    },
    async (req) => service.getPoolDetail(identity(req).user.id, req.params.id),
  );

  // PATCH /v1/pools/:id - Update pool
  router.patch(
    "/v1/pools/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "updatePool",
        params: z.object({ id: z.string().uuid() }),
        body: updatePoolSchema,
        response: { 200: poolDetailSchema, ...errors },
      },
    },
    async (req) => service.updatePool(identity(req).user.id, req.params.id, req.body),
  );

  // DELETE /v1/pools/:id - Cancel pool
  router.delete(
    "/v1/pools/:id",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "cancelPool",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.cancelPool(identity(req).user.id, req.params.id),
  );

  // POST /v1/pools/:id/knocks - Knock on pool
  router.post(
    "/v1/pools/:id/knocks",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "knockPool",
        params: z.object({ id: z.string().uuid() }),
        body: knockSchema,
        response: {
          201: z.object({
            id: z.string().uuid(),
            poolId: z.string().uuid(),
            userId: z.string().uuid(),
            message: z.string().nullable(),
            status: z.string(),
            createdAt: z.string(),
          }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const knock = await service.knock(identity(req).user.id, req.params.id, req.body);
      return reply.code(201).send(knock);
    },
  );

  // DELETE /v1/pools/:id/knocks/me - Withdraw knock
  router.delete(
    "/v1/pools/:id/knocks/me",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "withdrawKnock",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.withdrawKnock(identity(req).user.id, req.params.id),
  );

  // GET /v1/pools/:id/knocks - Organizer: list pending knocks
  router.get(
    "/v1/pools/:id/knocks",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listPoolKnocks",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.array(poolKnockItemSchema), ...errors },
      },
    },
    async (req) => service.listKnocks(identity(req).user.id, req.params.id),
  );

  // POST /v1/pools/:id/knocks/:knockId/accept - Organizer: accept knock
  router.post(
    "/v1/pools/:id/knocks/:knockId/accept",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "acceptPoolKnock",
        params: z.object({ id: z.string().uuid(), knockId: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.acceptKnock(identity(req).user.id, req.params.id, req.params.knockId),
  );

  // POST /v1/pools/:id/knocks/:knockId/decline - Organizer: decline knock
  router.post(
    "/v1/pools/:id/knocks/:knockId/decline",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "declinePoolKnock",
        params: z.object({ id: z.string().uuid(), knockId: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.declineKnock(identity(req).user.id, req.params.id, req.params.knockId),
  );

  // GET /v1/pools/:id/members - List members
  router.get(
    "/v1/pools/:id/members",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listPoolMembers",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.array(poolMemberSchema), ...errors },
      },
    },
    async (req) => service.listMembers(identity(req).user.id, req.params.id),
  );

  // DELETE /v1/pools/:id/members/me - Leave pool
  router.delete(
    "/v1/pools/:id/members/me",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "leavePool",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.leavePool(identity(req).user.id, req.params.id),
  );

  // DELETE /v1/pools/:id/members/:userId - Organizer remove member
  router.delete(
    "/v1/pools/:id/members/:userId",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "removePoolMember",
        params: z.object({ id: z.string().uuid(), userId: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.removeMember(identity(req).user.id, req.params.id, req.params.userId),
  );

  // GET /v1/me/pools - My pools
  router.get(
    "/v1/me/pools",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listMyPools",
        querystring: z.object({
          role: z.enum(["member", "organizer"]).optional(),
          when: z.enum(["upcoming", "past"]).optional(),
        }),
        response: { 200: z.array(poolItemSchema), ...errors },
      },
    },
    async (req) =>
      service.listMyPools(identity(req).user.id, req.query.role, req.query.when),
  );
}
