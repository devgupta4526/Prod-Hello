import { z } from "zod";

export const createOrderSchema = z
  .object({
    tierId: z.string().uuid(),
    quantity: z.number().int().positive().max(10),
    requestId: z.string().uuid(),
  })
  .strict();

export const orderResponseSchema = z.object({
  orderId: z.string().uuid(),
  amountPaise: z.number(),
  status: z.enum([
    "PENDING_PAYMENT",
    "PAID",
    "EXPIRED",
    "FAILED",
    "REFUNDED",
    "PARTIALLY_REFUNDED",
  ]),
  reservedUntil: z.string().nullable(),
  razorpay: z
    .object({
      keyId: z.string(),
      orderId: z.string(),
      amount: z.number(),
      currency: z.string(),
    })
    .nullable(),
});

export const confirmOrderSchema = z
  .object({
    razorpayPaymentId: z.string().min(1),
    razorpaySignature: z.string().min(1),
  })
  .strict();

export const ticketItemSchema = z.object({
  id: z.string().uuid(),
  eventId: z.string().uuid(),
  eventTitle: z.string(),
  venueName: z.string(),
  address: z.string(),
  startsAt: z.string(),
  tierName: z.string(),
  bookingRef: z.string(),
  qrValue: z.string(), // HLO1.<qrToken>
  status: z.enum(["CONFIRMED", "REFUNDED", "CANCELLED"]),
  checkedInAt: z.string().nullable(),
  createdAt: z.string(),
});
