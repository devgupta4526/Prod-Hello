import { z } from "zod";

export const presenceUpdateSchema = z
  .object({
    lat: z.number().min(-90).max(90),
    lng: z.number().min(-180).max(180),
    accuracyM: z.number().int().positive().max(10000).optional(),
    availability: z.enum(["OFF", "OPEN", "BROWSING", "AT_EVENT"]),
  })
  .strict();

export const nearbyQuerySchema = z.object({
  radiusKm: z.coerce.number().min(1).max(25).optional(),
  interests: z.string().optional(),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

export const discoveryUserItemSchema = z.object({
  userId: z.string().uuid(),
  displayName: z.string(),
  age: z.number().nullable(),
  photoUrl: z.string().nullable(),
  availability: z.enum(["OPEN", "BROWSING", "AT_EVENT"]),
  distanceBucket: z.string(),
  marker: z.object({
    lat: z.number(),
    lng: z.number(),
  }),
  sharedInterests: z.array(z.string()),
  upFor: z.string().nullable(),
  upForUntil: z.string().nullable(),
});

export const discoveryNearbyResponseSchema = z.object({
  items: z.array(discoveryUserItemSchema),
  nextCursor: z.string().nullable(),
});

export const publicUserProfileSchema = z.object({
  userId: z.string().uuid(),
  displayName: z.string(),
  bio: z.string().nullable(),
  prompts: z.array(z.object({ id: z.string(), answer: z.string() })),
  idealWeekend: z.string(),
  upFor: z.string().nullable(),
  upForUntil: z.string().nullable(),
  phoneVerified: z.boolean(),
  age: z.number().nullable(),
  photos: z.array(
    z.object({
      id: z.string().uuid(),
      url: z.string(),
      position: z.number(),
    }),
  ),
  interests: z.array(z.string()),
  availability: z.enum(["OFF", "OPEN", "BROWSING", "AT_EVENT"]).nullable(),
});
