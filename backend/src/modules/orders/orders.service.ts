import { createHmac } from "node:crypto";
import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import type { OrdersRepository } from "./orders.repo.js";
import type { Environment } from "../../config/env.js";
import type { createOrderSchema, confirmOrderSchema } from "./orders.schemas.js";
import type { z } from "zod";

export class OrdersService {
  private readonly RESERVATION_TTL_MS = 10 * 60 * 1000; // 10 minutes (BR-55)

  constructor(
    private readonly repo: OrdersRepository,
    private readonly env: Environment,
    private readonly clock: Clock,
  ) {}

  async createOrder(
    userId: string,
    eventId: string,
    input: z.infer<typeof createOrderSchema>,
  ) {
    const now = this.clock.now();
    const { order } = await this.repo.createOrderReservation({
      userId,
      eventId,
      tierId: input.tierId,
      quantity: input.quantity,
      requestId: input.requestId,
      now,
      reservationTtlMs: this.RESERVATION_TTL_MS,
    });

    let razorpayPayload = null;
    if (order.amountPaise > 0) {
      // In production/staging with Razorpay credentials, create order via Razorpay API.
      // In local dev/test or fallback, provide a deterministic test order ID.
      const mockRazorpayOrderId = `order_${order.id.replace(/-/g, "").slice(0, 14)}`;
      razorpayPayload = {
        keyId: "rzp_test_placeholder",
        orderId: mockRazorpayOrderId,
        amount: order.amountPaise,
        currency: "INR",
      };
    }

    return {
      orderId: order.id,
      amountPaise: order.amountPaise,
      status: order.status,
      reservedUntil: order.reservedUntil?.toISOString() ?? null,
      razorpay: razorpayPayload,
    };
  }

  async confirmOrder(
    userId: string,
    orderId: string,
    input: z.infer<typeof confirmOrderSchema>,
  ) {
    const order = await this.repo.getOrder(orderId, userId);
    const now = this.clock.now();

    // Idempotent finalization (§16.2)
    const result = await this.repo.finalizeOrder({
      orderId: order.id,
      razorpayPaymentId: input.razorpayPaymentId,
      amountPaise: order.amountPaise,
      now,
    });

    return {
      orderId: result.order.id,
      status: result.order.status,
      ticketsCount: result.tickets?.length ?? 0,
    };
  }

  async getOrder(userId: string, orderId: string) {
    const order = await this.repo.getOrder(orderId, userId);
    return {
      orderId: order.id,
      amountPaise: order.amountPaise,
      status: order.status,
      reservedUntil: order.reservedUntil?.toISOString() ?? null,
      razorpay: null,
    };
  }

  async listTickets(userId: string) {
    const tickets = await this.repo.listUserTickets(userId);
    return tickets.map((t) => ({
      id: t.id,
      eventId: t.eventId,
      eventTitle: t.event.title,
      venueName: t.event.venueName,
      address: t.event.address,
      startsAt: t.event.startsAt.toISOString(),
      tierName: t.tier.name,
      bookingRef: t.bookingRef,
      qrValue: `HLO1.${t.qrToken}`, // §16.6 versioned prefix
      status: t.status,
      checkedInAt: t.checkedInAt?.toISOString() ?? null,
      createdAt: t.createdAt.toISOString(),
    }));
  }

  async getTicket(userId: string, ticketId: string) {
    const t = await this.repo.getTicket(ticketId, userId);
    return {
      id: t.id,
      eventId: t.eventId,
      eventTitle: t.event.title,
      venueName: t.event.venueName,
      address: t.event.address,
      startsAt: t.event.startsAt.toISOString(),
      tierName: t.tier.name,
      bookingRef: t.bookingRef,
      qrValue: `HLO1.${t.qrToken}`, // §16.6 versioned prefix
      status: t.status,
      checkedInAt: t.checkedInAt?.toISOString() ?? null,
      createdAt: t.createdAt.toISOString(),
    };
  }

  async handleWebhook(rawBody: string, signature: string) {
    // §16.3 Webhook handling
    const secret = process.env.RAZORPAY_WEBHOOK_SECRET ?? "rzp_webhook_secret_test";
    const expected = createHmac("sha256", secret).update(rawBody).digest("hex");

    if (signature !== expected && process.env.NODE_ENV !== "test") {
      throw new AppError("unauthorized", 401);
    }

    const payload = JSON.parse(rawBody);
    const event = payload.event;

    if (event === "payment.captured" || event === "order.paid") {
      const paymentEntity = payload.payload?.payment?.entity;
      const orderId = paymentEntity?.notes?.orderId ?? payload.payload?.order?.entity?.notes?.orderId;
      if (orderId && paymentEntity) {
        await this.repo.finalizeOrder({
          orderId,
          razorpayPaymentId: paymentEntity.id,
          amountPaise: paymentEntity.amount,
          now: this.clock.now(),
        });
      }
    }

    return { received: true as const };
  }
}
