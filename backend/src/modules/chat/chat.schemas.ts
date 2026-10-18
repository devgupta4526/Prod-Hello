import { z } from "zod";

export const sendMessageSchema = z
  .object({
    clientMsgId: z.string().min(1).max(64),
    body: z.string().trim().min(1).max(1000),
  })
  .strict();

export const messageItemSchema = z.object({
  id: z.string().uuid(),
  conversationId: z.string().uuid(),
  senderId: z.string().uuid(),
  clientMsgId: z.string(),
  body: z.string(),
  deliveredAt: z.string().nullable(),
  deletedAt: z.string().nullable(),
  createdAt: z.string(),
  sender: z
    .object({
      id: z.string().uuid(),
      displayName: z.string(),
      photoUrl: z.string().nullable(),
    })
    .optional(),
});

export const conversationItemSchema = z.object({
  id: z.string().uuid(),
  type: z.enum(["DM", "POOL"]),
  title: z.string(),
  photoUrl: z.string().nullable(),
  partnerId: z.string().uuid().nullable(),
  poolId: z.string().uuid().nullable(),
  isMuted: z.boolean(),
  lastMessage: messageItemSchema.nullable(),
  createdAt: z.string(),
});

export const conversationDetailSchema = z.object({
  id: z.string().uuid(),
  type: z.enum(["DM", "POOL"]),
  title: z.string(),
  poolId: z.string().uuid().nullable(),
  isMuted: z.boolean(),
  participants: z.array(
    z.object({
      id: z.string().uuid(),
      displayName: z.string(),
      photoUrl: z.string().nullable(),
    }),
  ),
});

export const ackSchema = z
  .object({
    lastMessageId: z.string().uuid(),
  })
  .strict();

export const muteSchema = z
  .object({
    muted: z.boolean(),
  })
  .strict();
