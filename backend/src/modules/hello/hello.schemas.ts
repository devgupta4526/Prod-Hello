import { z } from "zod";
import { contextSchema } from "../social/social.schemas.js";

export const sendHelloSchema = z
  .object({
    context: contextSchema.optional(),
    receiverId: z.string().uuid(),
    message: z.string().trim().min(1).max(280),
  })
  .strict();

export const helloStatusSchema = z.enum([
  "PENDING",
  "ACCEPTED",
  "DECLINED",
  "EXPIRED",
  "WITHDRAWN",
  "CANCELLED_BY_BLOCK",
]);

export const helloItemSchema = z.object({
  id: z.string().uuid(),
  senderId: z.string().uuid(),
  receiverId: z.string().uuid(),
  message: z.string(),
  context: contextSchema.nullable(),
  status: helloStatusSchema,
  expiresAt: z.string(),
  createdAt: z.string(),
  sender: z
    .object({
      id: z.string().uuid(),
      displayName: z.string(),
      photoUrl: z.string().nullable(),
    })
    .optional(),
  receiver: z
    .object({
      id: z.string().uuid(),
      displayName: z.string(),
      photoUrl: z.string().nullable(),
    })
    .optional(),
});

export const sendHelloResponseSchema = z.object({
  hello: z.object({
    id: z.string().uuid(),
    status: helloStatusSchema,
    expiresAt: z.string(),
  }),
  connected: z.boolean(),
  connectionId: z.string().uuid().optional(),
});

export const listHellosQuerySchema = z.object({
  box: z.enum(["received", "sent"]).default("received"),
  status: helloStatusSchema.optional(),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

export const connectionItemSchema = z.object({
  id: z.string().uuid(),
  createdAt: z.string(),
  partner: z.object({
    id: z.string().uuid(),
    displayName: z.string(),
    photoUrl: z.string().nullable(),
  }),
  conversationId: z.string().uuid().nullable(),
});
