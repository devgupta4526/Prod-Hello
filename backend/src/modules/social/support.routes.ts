import { v7 as uuid } from "uuid";
import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";
import { memoryImage } from "./memory-image.js";

export function supportRoutes(app: FastifyInstance, db: Database) {
  const r = app.withTypeProvider<ZodTypeProvider>();
  const config = { access: "session" as const, returnsPeople: false };
  const params = z.object({ id: z.uuid() });
  const message = z.object({ requestId: z.uuid(), body: z.string().trim().min(2).max(2000), imageBase64: z.string().max(94000).optional() }).strict();
  function identity(req: FastifyRequest) {
    if (!req.identity || req.identity.user.status !== "ACTIVE") throw new AppError("forbidden", 403);
    return req.identity.user;
  }
  async function access(id: string, req: FastifyRequest) {
    const user = identity(req);
    const item = await db.supportCase.findUnique({ where: { id } });
    if (!item || (item.userId !== user.id && !["MODERATOR", "ADMIN"].includes(user.role))) throw new AppError("not_found", 404);
    return item;
  }
  r.post("/v1/tickets/:id/cancel", { config, schema: { params } }, async req => {
    const user = identity(req);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "Ticket" WHERE id = ${req.params.id}::uuid FOR UPDATE`;
      const ticket = await tx.ticket.findUnique({ where: { id: req.params.id }, include: { order: true, event: true } });
      if (!ticket || ticket.ownerId !== user.id) throw new AppError("not_found", 404);
      if (ticket.status === "CANCELLED") return { status: "CANCELLED", refundPaise: 0 };
      if (ticket.status !== "CONFIRMED" || ticket.checkedInAt || ticket.event.startsAt <= new Date()) throw new AppError("ticket_not_cancellable", 409);
      if (ticket.order.amountPaise > 0) throw new AppError("refund_provider_unavailable", 503);
      await tx.ticket.update({ where: { id: ticket.id }, data: { status: "CANCELLED" } });
      await tx.ticketTier.update({ where: { id: ticket.tierId }, data: { sold: { decrement: 1 } } });
      await tx.notification.upsert({ where: { userId_dedupeKey: { userId: user.id, dedupeKey: `ticket-cancelled:${ticket.id}` } }, create: { id: uuid(), userId: user.id, type: "TICKET_CANCELLED", dedupeKey: `ticket-cancelled:${ticket.id}`, payload: { ticketId: ticket.id, title: "Booking cancelled.", message: "Your free ticket has been cancelled. No payment was taken." } }, update: {} });
      return { status: "CANCELLED", refundPaise: 0 };
    });
  });
  r.get("/v1/me/support", { config }, async req => ({ items: await db.supportCase.findMany({ where: { userId: identity(req).id }, orderBy: { updatedAt: "desc" }, take: 100 }) }));
  r.post("/v1/me/support", { config, schema: { body: message.extend({ ticketId: z.uuid().optional(), issue: z.enum(["Payment or refund", "Tickets or entry", "Event changed", "Safety concern", "Something else"]) }) } }, async req => {
    const user = identity(req);
    if (req.body.ticketId && !await db.ticket.findFirst({ where: { id: req.body.ticketId, ownerId: user.id } })) throw new AppError("not_found", 404);
    const attachment = req.body.imageBase64 ? new Uint8Array(memoryImage(req.body.imageBase64)) : null;
    const existing = await db.supportCase.findUnique({ where: { id: req.body.requestId } });
    if (existing) {
      if (existing.userId !== user.id) throw new AppError("not_found", 404);
      return existing;
    }
    return db.$transaction(async tx => {
      const item = await tx.supportCase.create({ data: { id: req.body.requestId, userId: user.id, ticketId: req.body.ticketId ?? null, issue: req.body.issue, messages: { create: { id: uuid(), authorId: user.id, body: req.body.body, attachment } } } });
      await tx.notification.create({ data: { id: uuid(), userId: user.id, type: "SUPPORT_OPENED", dedupeKey: `support:${item.id}`, payload: { caseId: item.id, title: "Support request sent.", message: req.body.issue } } });
      return item;
    });
  });
  r.get("/v1/support/:id", { config, schema: { params } }, async req => {
    const item = await access(req.params.id, req);
    const messages = await db.supportMessage.findMany({ where: { caseId: item.id }, orderBy: [{ createdAt: "asc" }, { id: "asc" }], take: 200, select: { id: true, body: true, fromSupport: true, createdAt: true, attachment: true } });
    return { ...item, messages: messages.map(({ attachment, ...message }) => ({ ...message, hasAttachment: attachment !== null })) };
  });
  r.get("/v1/support/:id/attachments/:messageId", { config, schema: { params: params.extend({ messageId: z.uuid() }) } }, async (req, reply) => {
    await access(req.params.id, req);
    const message = await db.supportMessage.findFirst({ where: { id: req.params.messageId, caseId: req.params.id }, select: { attachment: true } });
    if (!message?.attachment) throw new AppError("not_found", 404);
    return reply.header("Cache-Control", "no-store").type("image/jpeg").send(Buffer.from(message.attachment));
  });
  r.post("/v1/support/:id/replies", { config, schema: { params, body: message } }, async req => {
    const item = await access(req.params.id, req);
    const user = identity(req);
    const attachment = req.body.imageBase64 ? new Uint8Array(memoryImage(req.body.imageBase64)) : null;
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SupportCase" WHERE id = ${item.id}::uuid FOR UPDATE`;
      const current = await tx.supportCase.findUniqueOrThrow({ where: { id: item.id } });
      const existing = await tx.supportMessage.findUnique({ where: { id: req.body.requestId } });
      if (existing) {
        if (existing.caseId !== item.id || existing.authorId !== user.id) throw new AppError("not_found", 404);
        return { ok: true };
      }
      if (current.status !== "OPEN") throw new AppError("support_closed", 409);
      const fromSupport = item.userId !== user.id;
      await tx.supportMessage.create({ data: { id: req.body.requestId, caseId: item.id, authorId: user.id, fromSupport, body: req.body.body, attachment } });
      await tx.supportCase.update({ where: { id: item.id }, data: { updatedAt: new Date() } });
      if (fromSupport) await tx.notification.create({ data: { id: uuid(), userId: item.userId, type: "SUPPORT_REPLY", dedupeKey: `support-reply:${req.body.requestId}`, payload: { caseId: item.id, title: "Romear Support replied", message: "Open your support conversation to read the response." } } });
      return { ok: true };
    });
  });
}
