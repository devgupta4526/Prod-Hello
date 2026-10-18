import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { DiscoveryService } from "./discovery.service.js";
import {
  presenceUpdateSchema,
  nearbyQuerySchema,
  discoveryNearbyResponseSchema,
  publicUserProfileSchema,
} from "./discovery.schemas.js";
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

export function discoveryRoutes(
  app: FastifyInstance,
  service: DiscoveryService,
) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // PUT /v1/presence - update presence
  router.put(
    "/v1/presence",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "updatePresence",
        body: presenceUpdateSchema,
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.updatePresence(identity(req).user.id, req.body),
  );

  // DELETE /v1/presence - go OFF
  router.delete(
    "/v1/presence",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "clearPresence",
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.clearPresence(identity(req).user.id),
  );

  // GET /v1/discovery/nearby - nearby users (returns people)
  router.get(
    "/v1/discovery/nearby",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getNearbyUsers",
        querystring: nearbyQuerySchema,
        response: { 200: discoveryNearbyResponseSchema, ...errors },
      },
    },
    async (req) => service.getNearby(identity(req).user.id, req.query),
  );

  // GET /v1/users/:id - public profile (returns people)
  router.get(
    "/v1/users/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getUserPublicProfile",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: publicUserProfileSchema, ...errors },
      },
    },
    async (req) => service.getUserProfile(identity(req).user.id, req.params.id),
  );
}
