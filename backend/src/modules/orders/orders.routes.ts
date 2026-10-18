import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { OrdersService } from "./orders.service.js";
import {
  createOrderSchema,
  confirmOrderSchema,
  orderResponseSchema,
  ticketItemSchema,
} from "./orders.schemas.js";
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

export function ordersRoutes(app: FastifyInstance, service: OrdersService) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // POST /v1/events/:id/orders - Reserve order
  router.post(
    "/v1/events/:id/orders",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "createOrder",
        params: z.object({ id: z.string().uuid() }),
        body: createOrderSchema,
        response: { 201: orderResponseSchema, ...errors },
      },
    },
    async (req, reply) => {
      const order = await service.createOrder(
        identity(req).user.id,
        req.params.id,
        req.body,
      );
      return reply.code(201).send(order);
    },
  );

  // POST /v1/orders/:id/confirm - Confirm order payment
  router.post(
    "/v1/orders/:id/confirm",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "confirmOrder",
        params: z.object({ id: z.string().uuid() }),
        body: confirmOrderSchema,
        response: {
          200: z.object({
            orderId: z.string().uuid(),
            status: z.string(),
            ticketsCount: z.number(),
          }),
          ...errors,
        },
      },
    },
    async (req) =>
      service.confirmOrder(identity(req).user.id, req.params.id, req.body),
  );

  // GET /v1/orders/:id - Poll order status
  router.get(
    "/v1/orders/:id",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getOrderStatus",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: orderResponseSchema, ...errors },
      },
    },
    async (req) => service.getOrder(identity(req).user.id, req.params.id),
  );

  // GET /v1/me/tickets - User tickets
  router.get(
    "/v1/me/tickets",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "listMyTickets",
        response: { 200: z.array(ticketItemSchema), ...errors },
      },
    },
    async (req) => service.listTickets(identity(req).user.id),
  );

  // GET /v1/tickets/:id - Single ticket with QR token
  router.get(
    "/v1/tickets/:id",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getTicketDetail",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: ticketItemSchema, ...errors },
      },
    },
    async (req) => service.getTicket(identity(req).user.id, req.params.id),
  );

  // POST /v1/webhooks/razorpay - Razorpay webhook
  router.post(
    "/v1/webhooks/razorpay",
    {
      config: { access: "public", returnsPeople: false },
      schema: {
        operationId: "razorpayWebhook",
        response: { 200: z.object({ received: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      const signature = (req.headers["x-razorpay-signature"] as string) ?? "";
      return service.handleWebhook(JSON.stringify(req.body), signature);
    },
  );
}
