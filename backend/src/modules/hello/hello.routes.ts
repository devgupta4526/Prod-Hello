import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { HelloService } from "./hello.service.js";
import {
  sendHelloSchema,
  sendHelloResponseSchema,
  listHellosQuerySchema,
  helloItemSchema,
  connectionItemSchema,
} from "./hello.schemas.js";
import { errorSchema } from "../system/system.schemas.js";

const errors = {
  400: errorSchema,
  401: errorSchema,
  403: errorSchema,
  404: errorSchema,
  409: errorSchema,
  410: errorSchema,
  429: errorSchema,
  503: errorSchema,
};

function identity(request: FastifyRequest) {
  if (!request.identity) throw new AppError("unauthorized", 401);
  return request.identity;
}

export function helloRoutes(app: FastifyInstance, service: HelloService) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // POST /v1/hellos - Send Hello (or mutual auto-accept)
  router.post(
    "/v1/hellos",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "sendHello",
        body: sendHelloSchema,
        response: { 201: sendHelloResponseSchema, ...errors },
      },
    },
    async (req, reply) => {
      const result = await service.sendHello(identity(req).user.id, req.body);
      return reply.code(201).send(result);
    },
  );

  // GET /v1/hellos - List received or sent hellos
  router.get(
    "/v1/hellos",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listHellos",
        querystring: listHellosQuerySchema,
        response: {
          200: z.object({
            items: z.array(helloItemSchema),
            nextCursor: z.string().nullable(),
          }),
          ...errors,
        },
      },
    },
    async (req) => service.listHellos(identity(req).user.id, req.query),
  );

  // GET /v1/hellos/:id - Detail of a Hello
  router.get(
    "/v1/hellos/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getHelloDetail",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: helloItemSchema, ...errors },
      },
    },
    async (req) => service.getHello(identity(req).user.id, req.params.id),
  );

  // POST /v1/hellos/:id/accept - Receiver accepts
  router.post(
    "/v1/hellos/:id/accept",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "acceptHello",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: sendHelloResponseSchema, ...errors },
      },
    },
    async (req) => service.acceptHello(identity(req).user.id, req.params.id),
  );

  // POST /v1/hellos/:id/decline - Receiver declines (silent)
  router.post(
    "/v1/hellos/:id/decline",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "declineHello",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.declineHello(identity(req).user.id, req.params.id),
  );

  // POST /v1/hellos/:id/withdraw - Sender withdraws
  router.post(
    "/v1/hellos/:id/withdraw",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "withdrawHello",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.withdrawHello(identity(req).user.id, req.params.id),
  );

  // GET /v1/connections - List user connections
  router.get(
    "/v1/connections",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listConnections",
        response: { 200: z.array(connectionItemSchema), ...errors },
      },
    },
    async (req) => service.listConnections(identity(req).user.id),
  );

  // DELETE /v1/connections/:id - Disconnect
  router.delete(
    "/v1/connections/:id",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "disconnect",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.disconnect(identity(req).user.id, req.params.id),
  );
}
