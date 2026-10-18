import { z } from "zod";

export const createPoolSchema = z
  .object({
    title: z.string().trim().min(3).max(80),
    description: z.string().trim().min(10).max(500),
    category: z.string().min(2).max(50),
    startsAt: z.string().datetime(),
    endsAt: z.string().datetime().optional(),
    capacity: z.number().int().min(2).max(12),
    venueName: z.string().min(2).max(100),
    venueLat: z.number().min(-90).max(90),
    venueLng: z.number().min(-180).max(180),
    areaLabel: z.string().min(2).max(100),
  })
  .strict();

export const updatePoolSchema = z
  .object({
    title: z.string().trim().min(3).max(80).optional(),
    description: z.string().trim().min(10).max(500).optional(),
    category: z.string().min(2).max(50).optional(),
    startsAt: z.string().datetime().optional(),
    endsAt: z.string().datetime().optional(),
    capacity: z.number().int().min(2).max(12).optional(),
    venueName: z.string().min(2).max(100).optional(),
    venueLat: z.number().min(-90).max(90).optional(),
    venueLng: z.number().min(-180).max(180).optional(),
    areaLabel: z.string().min(2).max(100).optional(),
  })
  .strict();

export const knockSchema = z
  .object({
    message: z.string().trim().max(120).optional(),
  })
  .strict();

export const poolItemSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  description: z.string(),
  category: z.string(),
  startsAt: z.string(),
  endsAt: z.string().nullable(),
  capacity: z.number(),
  memberCount: z.number(),
  status: z.enum(["OPEN", "FULL", "CLOSED", "CANCELLED", "ARCHIVED"]),
  areaLabel: z.string(),
  // Venue only revealed if isMember is true (BR-45)
  venueName: z.string().nullable(),
  isMember: z.boolean(),
  isOrganizer: z.boolean(),
  organizer: z.object({
    id: z.string().uuid(),
    displayName: z.string(),
    photoUrl: z.string().nullable(),
  }),
});

export const poolDetailSchema = poolItemSchema.extend({
  venueLat: z.number().nullable(),
  venueLng: z.number().nullable(),
  conversationId: z.string().uuid().nullable(),
  myKnockStatus: z.enum(["PENDING", "ACCEPTED", "DECLINED", "WITHDRAWN", "EXPIRED"]).nullable(),
});

export const poolMemberSchema = z.object({
  userId: z.string().uuid(),
  displayName: z.string(),
  photoUrl: z.string().nullable(),
  joinedAt: z.string(),
});

export const poolKnockItemSchema = z.object({
  id: z.string().uuid(),
  poolId: z.string().uuid(),
  userId: z.string().uuid(),
  message: z.string().nullable(),
  status: z.enum(["PENDING", "ACCEPTED", "DECLINED", "WITHDRAWN", "EXPIRED"]),
  createdAt: z.string(),
  user: z.object({
    id: z.string().uuid(),
    displayName: z.string(),
    photoUrl: z.string().nullable(),
  }),
});
