import { randomBytes } from "node:crypto";
import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";

function generateBookingRef(): string {
  const chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // No confusing 0/O, 1/I
  let ref = "HLO-";
  for (let i = 0; i < 4; i++) {
    ref += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  ref += "-";
  for (let i = 0; i < 4; i++) {
    ref += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return ref;
}

function generateQrToken(): string {
  return randomBytes(32).toString("base64url");
}

export class OrdersRepository {
  constructor(private readonly db: Database) {}

  async createOrderReservation(input: {
    userId: string;
    eventId: string;
    tierId: string;
    quantity: number;
    requestId: string;
    now: Date;
    reservationTtlMs: number;
  }) {
    return this.db.$transaction(async (tx) => {
      // 1. Idempotency check with (userId, requestId)
      const existing = await tx.order.findUnique({
        where: {
          userId_requestId: {
            userId: input.userId,
            requestId: input.requestId,
          },
        },
      });
      if (existing) return { order: existing, isNew: false };

      // 2. Fetch event & tier
      const event = await tx.event.findUnique({
        where: { id: input.eventId },
      });
      if (!event || event.status !== "PUBLISHED") {
        throw new AppError("event_not_available", 400);
      }

      const tier = await tx.ticketTier.findUnique({
        where: { id: input.tierId },
      });
      if (!tier || tier.eventId !== input.eventId) {
        throw new AppError("tier_not_found", 404);
      }

      if (input.quantity > tier.maxPerOrder) {
        throw new AppError("max_per_order_exceeded", 400);
      }

      // 3. Conditional reservation per §16.1
      // UPDATE tier SET reserved = reserved + qty WHERE id = tierId AND reserved + sold + qty <= capacity
      const updatedTier = await tx.ticketTier.updateMany({
        where: {
          id: input.tierId,
          reserved: {
            lte: tier.capacity - tier.sold - input.quantity,
          },
        },
        data: {
          reserved: { increment: input.quantity },
        },
      });

      if (updatedTier.count === 0) {
        throw new AppError("sold_out", 409); // BR-52 / §16.1
      }

      const orderId = uuid();
      const amountPaise = tier.pricePaise * input.quantity;
      const platformFeePaise = Math.round(amountPaise * 0.05); // 5% platform fee
      const reservedUntil = new Date(input.now.getTime() + input.reservationTtlMs);

      // If free tier (§16.5) -> issue immediately
      if (tier.pricePaise === 0) {
        // Move reserved -> sold
        await tx.ticketTier.update({
          where: { id: tier.id },
          data: {
            reserved: { decrement: input.quantity },
            sold: { increment: input.quantity },
          },
        });

        const order = await tx.order.create({
          data: {
            id: orderId,
            userId: input.userId,
            eventId: input.eventId,
            tierId: input.tierId,
            quantity: input.quantity,
            amountPaise: 0,
            platformFeePaise: 0,
            status: "PAID",
            requestId: input.requestId,
            createdAt: input.now,
          },
        });

        // Issue tickets
        for (let i = 0; i < input.quantity; i++) {
          await tx.ticket.create({
            data: {
              id: uuid(),
              orderId,
              eventId: input.eventId,
              tierId: input.tierId,
              ownerId: input.userId,
              bookingRef: generateBookingRef(),
              qrToken: generateQrToken(),
              status: "CONFIRMED",
              createdAt: input.now,
            },
          });
        }

        return { order, isNew: true };
      }

      // Paid order: create PENDING_PAYMENT
      const order = await tx.order.create({
        data: {
          id: orderId,
          userId: input.userId,
          eventId: input.eventId,
          tierId: input.tierId,
          quantity: input.quantity,
          amountPaise,
          platformFeePaise,
          status: "PENDING_PAYMENT",
          requestId: input.requestId,
          reservedUntil,
          createdAt: input.now,
        },
      });

      return { order, isNew: true };
    });
  }

  async finalizeOrder(input: {
    orderId: string;
    razorpayPaymentId: string;
    amountPaise: number;
    now: Date;
  }) {
    // §16.2 Single idempotent function
    return this.db.$transaction(async (tx) => {
      const order = await tx.order.findUnique({
        where: { id: input.orderId },
        include: { event: true, tier: true },
      });

      if (!order) throw new AppError("not_found", 404);

      // Step 1: Idempotency check
      if (order.status === "PAID") {
        return { order, alreadyFinalized: true };
      }

      // Step 3: Verify amount equals order
      if (input.amountPaise !== order.amountPaise) {
        throw new AppError("amount_mismatch", 400);
      }

      // Step 4: Move reserved -> sold on tier
      await tx.ticketTier.update({
        where: { id: order.tierId },
        data: {
          reserved: { decrement: order.quantity },
          sold: { increment: order.quantity },
        },
      });

      // Update Order status
      const updatedOrder = await tx.order.update({
        where: { id: order.id },
        data: { status: "PAID" },
      });

      // Insert Payment
      await tx.payment.create({
        data: {
          id: uuid(),
          orderId: order.id,
          razorpayPaymentId: input.razorpayPaymentId,
          amountPaise: input.amountPaise,
          status: "captured",
          capturedAt: input.now,
        },
      });

      // Create quantity tickets
      const tickets = [];
      for (let i = 0; i < order.quantity; i++) {
        const ticket = await tx.ticket.create({
          data: {
            id: uuid(),
            orderId: order.id,
            eventId: order.eventId,
            tierId: order.tierId,
            ownerId: order.userId,
            bookingRef: generateBookingRef(),
            qrToken: generateQrToken(),
            status: "CONFIRMED",
            createdAt: input.now,
          },
        });
        tickets.push(ticket);
      }

      // Append ledger entries (§16.2 step 4)
      // SALE +gross
      await tx.ledgerEntry.create({
        data: {
          id: uuid(),
          organizerId: order.event.organizerId,
          eventId: order.eventId,
          orderId: order.id,
          type: "SALE",
          amountPaise: order.amountPaise,
          memo: `Ticket sale order ${order.id}`,
          createdAt: input.now,
        },
      });

      // PLATFORM_FEE -fee
      if (order.platformFeePaise > 0) {
        await tx.ledgerEntry.create({
          data: {
            id: uuid(),
            organizerId: order.event.organizerId,
            eventId: order.eventId,
            orderId: order.id,
            type: "PLATFORM_FEE",
            amountPaise: -order.platformFeePaise,
            memo: `Platform fee order ${order.id}`,
            createdAt: input.now,
          },
        });
      }

      // Notification
      await tx.notification.create({
        data: {
          id: uuid(),
          userId: order.userId,
          type: "ticket.issued",
          payload: { orderId: order.id, eventId: order.eventId },
          createdAt: input.now,
        },
      });

      return { order: updatedOrder, tickets, alreadyFinalized: false };
    });
  }

  async getOrder(orderId: string, userId: string) {
    const order = await this.db.order.findUnique({
      where: { id: orderId },
      include: { event: true, tier: true, tickets: true },
    });
    if (!order) throw new AppError("not_found", 404);
    if (order.userId !== userId) throw new AppError("forbidden", 403);
    return order;
  }

  async listUserTickets(userId: string) {
    return this.db.ticket.findMany({
      where: { ownerId: userId },
      include: {
        event: true,
        tier: true,
      },
      orderBy: { createdAt: "desc" },
    });
  }

  async getTicket(ticketId: string, userId: string) {
    const ticket = await this.db.ticket.findUnique({
      where: { id: ticketId },
      include: {
        event: true,
        tier: true,
      },
    });
    if (!ticket) throw new AppError("not_found", 404);
    if (ticket.ownerId !== userId) throw new AppError("forbidden", 403);
    return ticket;
  }

  async releaseExpiredReservations(now: Date) {
    // §16.4 Reservation expiry job
    const expiredOrders = await this.db.order.findMany({
      where: {
        status: "PENDING_PAYMENT",
        reservedUntil: { lt: now },
      },
    });

    for (const order of expiredOrders) {
      await this.db.$transaction(async (tx) => {
        await tx.ticketTier.update({
          where: { id: order.tierId },
          data: { reserved: { decrement: order.quantity } },
        });

        await tx.order.update({
          where: { id: order.id },
          data: { status: "EXPIRED" },
        });
      });
    }

    return expiredOrders.length;
  }
}
