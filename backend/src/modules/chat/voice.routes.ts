import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";
import { ChatRepository } from "./chat.repo.js";
import type { ChatService } from "./chat.service.js";
import type { DiscoveryService } from "../discovery/discovery.service.js";

export function voiceRoutes(app: FastifyInstance, db: Database, chatService: ChatService, discovery: DiscoveryService) {
  const r = app.withTypeProvider<ZodTypeProvider>();
  const config = { access: "session" as const, returnsPeople: true };
  const chat = new ChatRepository(db);
  const body = z.object({ requestId: z.uuid(), audioBase64: z.string().min(100).max(134000), durationMs: z.number().int().min(500).max(15500) }).strict();
  function viewer(req: FastifyRequest) {
    if (!req.identity || req.identity.user.status !== "ACTIVE") throw new AppError("forbidden", 403);
    return req.identity.user.id;
  }
  function audio(base64: string) {
    const bytes = Buffer.from(base64, "base64");
    if (bytes.length < 100 || bytes.length > 100000 || bytes.toString("ascii", 4, 8) !== "ftyp") throw new AppError("invalid_audio", 400);
    return bytes;
  }
  r.post("/v1/conversations/:id/voice", { config, schema: { params: z.object({ id: z.uuid() }), body } }, async req => {
    const userId = viewer(req);
    await chat.getConversationForUser(req.params.id, userId);
    const existing = await db.voiceClip.findUnique({ where: { id: req.body.requestId } });
    if (existing && (existing.userId !== userId || existing.conversationId !== req.params.id)) throw new AppError("forbidden", 403);
    const clip = existing ?? await db.voiceClip.create({ data: { id: req.body.requestId, userId, conversationId: req.params.id, audio: audio(req.body.audioBase64), durationMs: req.body.durationMs } });
    await chatService.sendMessage(req.params.id, userId, { clientMsgId: clip.id, body: `[VOICE:${clip.id}:${clip.durationMs}]` });
    return { id: clip.id, durationMs: clip.durationMs };
  });
  r.post("/v1/me/voice", { config, schema: { body } }, async req => {
    const userId = viewer(req);
    const existing = await db.voiceClip.findUnique({ where: { id: req.body.requestId } });
    if (existing && (existing.userId !== userId || existing.conversationId !== null)) throw new AppError("forbidden", 403);
    const clip = existing ?? await db.voiceClip.create({ data: { id: req.body.requestId, userId, audio: audio(req.body.audioBase64), durationMs: req.body.durationMs } });
    await db.voiceClip.deleteMany({ where: { userId, conversationId: null, id: { not: clip.id }, createdAt: { lt: clip.createdAt } } });
    return { id: clip.id, durationMs: clip.durationMs };
  });
  r.get("/v1/me/voice", { config }, async req => {
    const clip = await db.voiceClip.findFirst({ where: { userId: viewer(req), conversationId: null }, orderBy: { createdAt: "desc" }, select: { id: true, durationMs: true } });
    return { clip };
  });
  r.get("/v1/voice/:id", { config, schema: { params: z.object({ id: z.uuid() }) } }, async (req, reply) => {
    const userId = viewer(req);
    const clip = await db.voiceClip.findUnique({ where: { id: req.params.id } });
    if (!clip) throw new AppError("not_found", 404);
    if (clip.conversationId) await chat.getConversationForUser(clip.conversationId, userId);
    else if (clip.userId !== userId) await discovery.getUserProfile(userId, clip.userId);
    return reply.header("Cache-Control", "no-store").type("audio/mp4").send(Buffer.from(clip.audio));
  });
}
