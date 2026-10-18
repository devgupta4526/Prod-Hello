import { z } from "zod";

export const organizerApplicationSchema = z
  .object({
    orgName: z.string().trim().min(2).max(100),
    description: z.string().trim().min(10).max(1000),
    links: z.array(z.string().url()).optional(),
  })
  .strict();

export const createEventSchema = z
  .object({
    title: z.string().trim().min(3).max(100),
    description: z.string().trim().min(10).max(2000),
    category: z.string().min(2).max(50),
    venueName: z.string().min(2).max(100),
    address: z.string().min(5).max(200),
    lat: z.number().min(-90).max(90),
    lng: z.number().min(-180).max(180),
    coverKey: z.string().optional(),
    startsAt: z.string().datetime(),
    endsAt: z.string().datetime(),
    salesStartAt: z.string().datetime().optional(),
    salesEndAt: z.string().datetime().optional(),
  })
  .strict();

export const updateEventSchema = createEventSchema.partial();

export const createTierSchema = z
  .object({
    name: z.string().trim().min(1).max(50),
    pricePaise: z.number().int().nonnegative(),
    capacity: z.number().int().positive(),
    maxPerOrder: z.number().int().positive().max(10).default(6),
    sortOrder: z.number().int().default(0),
  })
  .strict();

export const updateTierSchema = createTierSchema.partial();

export const ticketTierItemSchema = z.object({
  id: z.string().uuid(),
  eventId: z.string().uuid(),
  name: z.string(),
  pricePaise: z.number(),
  capacity: z.number(),
  available: z.number(),
  maxPerOrder: z.number(),
  sortOrder: z.number(),
});

export const eventItemSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  description: z.string(),
  category: z.string(),
  venueName: z.string(),
  address: z.string(),
  lat: z.number(),
  lng: z.number(),
  coverUrl: z.string().nullable(),
  startsAt: z.string(),
  endsAt: z.string(),
  status: z.enum(["DRAFT", "PUBLISHED", "CANCELLED", "ENDED"]),
  minPricePaise: z.number().nullable(),
  organizer: z.object({
    id: z.string().uuid(),
    displayName: z.string(),
    photoUrl: z.string().nullable(),
  }),
});

export const eventDetailSchema = eventItemSchema.extend({
  tiers: z.array(ticketTierItemSchema),
});

export const checkinRequestSchema = z
  .object({
    token: z.string().min(1), // qrToken or bookingRef
    clientScanId: z.string().uuid().optional(),
  })
  .strict();

export const checkinResponseSchema = z.object({
  result: z.enum([
    "VALID",
    "ALREADY_USED",
    "INVALID",
    "WRONG_EVENT",
    "NOT_CONFIRMED",
    "REFUNDED",
  ]),
  holder: z.object({ displayName: z.string() }).optional(),
  tier: z.string().optional(),
  checkedInAt: z.string().optional(),
});
