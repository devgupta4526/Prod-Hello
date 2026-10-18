import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import { AppError } from "../../lib/app-error.js";
import type { EventsService } from "./events.service.js";
import {
  organizerApplicationSchema,
  createEventSchema,
  updateEventSchema,
  createTierSchema,
  updateTierSchema,
  eventItemSchema,
  eventDetailSchema,
  checkinRequestSchema,
  checkinResponseSchema,
} from "./events.schemas.js";
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

export function eventsRoutes(app: FastifyInstance, service: EventsService) {
  const router = app.withTypeProvider<ZodTypeProvider>();

  // GET /v1/events/nearby - Public nearby events
  router.get(
    "/v1/events/nearby",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listPublicEvents",
        querystring: z.object({
          category: z.string().optional(),
        }),
        response: { 200: z.array(eventItemSchema), ...errors },
      },
    },
    async (req) => service.listPublicEvents(req.query.category),
  );

  // GET /v1/events/:id - Event detail
  router.get(
    "/v1/events/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "getEventDetail",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: eventDetailSchema, ...errors },
      },
    },
    async (req) => service.getEventDetail(req.params.id),
  );

  // POST /v1/organizer/applications - Apply to be organizer
  router.post(
    "/v1/organizer/applications",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "applyOrganizer",
        body: organizerApplicationSchema,
        response: {
          201: z.object({
            id: z.string().uuid(),
            status: z.string(),
          }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const appRecord = await service.applyForOrganizer(
        identity(req).user.id,
        req.body,
      );
      return reply.code(201).send({ id: appRecord.id, status: appRecord.status });
    },
  );

  // GET /v1/organizer/events - Organizer's events
  router.get(
    "/v1/organizer/events",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "listOrganizerEvents",
        response: {
          200: z.array(
            z.object({
              id: z.string().uuid(),
              title: z.string(),
              status: z.string(),
              startsAt: z.string(),
              endsAt: z.string(),
              totalCapacity: z.number(),
              totalSold: z.number(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => service.listOrganizerEvents(identity(req).user.id),
  );

  // POST /v1/organizer/events - Create draft event
  router.post(
    "/v1/organizer/events",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "createEvent",
        body: createEventSchema,
        response: { 201: eventDetailSchema, ...errors },
      },
    },
    async (req, reply) => {
      const event = await service.createEvent(identity(req).user.id, req.body);
      return reply.code(201).send(event);
    },
  );

  // PATCH /v1/organizer/events/:id - Edit event
  router.patch(
    "/v1/organizer/events/:id",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "updateEvent",
        params: z.object({ id: z.string().uuid() }),
        body: updateEventSchema,
        response: { 200: eventDetailSchema, ...errors },
      },
    },
    async (req) =>
      service.updateEvent(identity(req).user.id, req.params.id, req.body),
  );

  // POST /v1/organizer/events/:id/publish - Publish event
  router.post(
    "/v1/organizer/events/:id/publish",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "publishEvent",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: eventDetailSchema, ...errors },
      },
    },
    async (req) => service.publishEvent(identity(req).user.id, req.params.id),
  );

  // POST /v1/organizer/events/:id/cancel - Cancel event
  router.post(
    "/v1/organizer/events/:id/cancel",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "cancelEvent",
        params: z.object({ id: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => service.cancelEvent(identity(req).user.id, req.params.id),
  );

  // POST /v1/organizer/events/:id/tiers - Create tier
  router.post(
    "/v1/organizer/events/:id/tiers",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "createTier",
        params: z.object({ id: z.string().uuid() }),
        body: createTierSchema,
        response: {
          201: z.object({
            id: z.string().uuid(),
            name: z.string(),
            pricePaise: z.number(),
            capacity: z.number(),
          }),
          ...errors,
        },
      },
    },
    async (req, reply) => {
      const tier = await service.createTier(
        identity(req).user.id,
        req.params.id,
        req.body,
      );
      return reply.code(201).send({
        id: tier.id,
        name: tier.name,
        pricePaise: tier.pricePaise,
        capacity: tier.capacity,
      });
    },
  );

  // PATCH /v1/organizer/events/:id/tiers/:tierId - Update tier
  router.patch(
    "/v1/organizer/events/:id/tiers/:tierId",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "updateTier",
        params: z.object({ id: z.string().uuid(), tierId: z.string().uuid() }),
        body: updateTierSchema,
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) => {
      await service.updateTier(
        identity(req).user.id,
        req.params.id,
        req.params.tierId,
        req.body,
      );
      return { ok: true as const };
    },
  );

  // DELETE /v1/organizer/events/:id/tiers/:tierId - Delete tier
  router.delete(
    "/v1/organizer/events/:id/tiers/:tierId",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "deleteTier",
        params: z.object({ id: z.string().uuid(), tierId: z.string().uuid() }),
        response: { 200: z.object({ ok: z.literal(true) }), ...errors },
      },
    },
    async (req) =>
      service.deleteTier(
        identity(req).user.id,
        req.params.id,
        req.params.tierId,
      ),
  );

  // GET /v1/organizer/events/:id/attendees - Attendees
  router.get(
    "/v1/organizer/events/:id/attendees",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "listAttendees",
        params: z.object({ id: z.string().uuid() }),
        querystring: z.object({ search: z.string().optional() }),
        response: {
          200: z.array(
            z.object({
              id: z.string().uuid(),
              bookingRef: z.string(),
              tierName: z.string(),
              checkedInAt: z.string().nullable(),
              holder: z.object({ displayName: z.string() }),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) =>
      service.listAttendees(
        identity(req).user.id,
        req.params.id,
        req.query.search,
      ),
  );

  // GET /v1/organizer/events/:id/summary - Summary stats
  router.get(
    "/v1/organizer/events/:id/summary",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getEventSummary",
        params: z.object({ id: z.string().uuid() }),
        response: {
          200: z.object({
            eventId: z.string().uuid(),
            title: z.string(),
            status: z.string(),
            totalCapacity: z.number(),
            totalSold: z.number(),
            checkedInCount: z.number(),
            revenuePaise: z.number(),
          }),
          ...errors,
        },
      },
    },
    async (req) => service.getSummary(identity(req).user.id, req.params.id),
  );

  // GET /v1/organizer/ledger - Ledger entries
  router.get(
    "/v1/organizer/ledger",
    {
      config: { access: "session", returnsPeople: false },
      schema: {
        operationId: "getOrganizerLedger",
        response: {
          200: z.array(
            z.object({
              id: z.string().uuid(),
              eventId: z.string().uuid(),
              eventTitle: z.string(),
              type: z.string(),
              amountPaise: z.number(),
              memo: z.string().nullable(),
              createdAt: z.string(),
            }),
          ),
          ...errors,
        },
      },
    },
    async (req) => service.getLedger(identity(req).user.id),
  );

  // POST /v1/events/:id/checkins - Check-in scanner
  router.post(
    "/v1/events/:id/checkins",
    {
      config: { access: "session", returnsPeople: true },
      schema: {
        operationId: "checkinAttendee",
        params: z.object({ id: z.string().uuid() }),
        body: checkinRequestSchema,
        response: { 200: checkinResponseSchema, ...errors },
      },
    },
    async (req) =>
      service.checkin(identity(req).user.id, req.params.id, req.body),
  );
}
