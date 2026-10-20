import { v7 as uuid } from "uuid";
import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import type { HelloRepository } from "./hello.repo.js";
import type { SafetyRepository } from "../safety/safety.repo.js";
import type { DiscoveryRepository } from "../discovery/discovery.repo.js";
import type { sendHelloSchema, listHellosQuerySchema } from "./hello.schemas.js";
import type { z } from "zod";

export class HelloService {
  private readonly HELLO_EXPIRY_MS = 48 * 60 * 60 * 1000; // 48 hours (BR-22)
  private readonly DAILY_SEND_CAP = 10; // OD-5

  constructor(
    private readonly repo: HelloRepository,
    private readonly safetyRepo: SafetyRepository,
    private readonly discoveryRepo: DiscoveryRepository,
    private readonly clock: Clock,
  ) {}

  async sendHello(senderId: string, input: z.infer<typeof sendHelloSchema>) {
    const receiverId = input.receiverId;
    if (senderId === receiverId) {
      throw new AppError("cannot_send_to_self", 400);
    }

    const now = this.clock.now();

    // BR-11: Block check
    const isBlocked = await this.safetyRepo.isBlockedPair(senderId, receiverId);
    if (isBlocked) {
      throw new AppError("not_found", 404);
    }

    // BR-26: Check receiver's discoverability and availability (OPEN or AT_EVENT)
    const receiverPresence = await this.discoveryRepo.getPresence(receiverId);
    if (
      !receiverPresence ||
      receiverPresence.expiresAt <= now ||
      !["OPEN", "AT_EVENT"].includes(receiverPresence.availability)
    ) {
      throw new AppError("user_unavailable", 400);
    }

    let context: { type: string; id: string; text: string } | undefined;
    const replyContext = input.context;
    if (replyContext) {
      const profile = await this.discoveryRepo.getPublicProfile(receiverId);
      if (!profile || profile.status !== "ACTIVE") throw new AppError("not_found", 404);
      if (replyContext.type === "PHOTO") {
        const photo = replyContext.id === "current" ? profile.photos[0] : profile.photos.find(p => p.id === replyContext.id);
        if (!photo) throw new AppError("context_unavailable", 409);
        context = { type: "PHOTO", id: photo.id, text: `Photo ${String(photo.position + 1)}` };
      } else if (replyContext.type === "ICEBREAKER") {
        const day = new Date(now.getTime() + 19800000).toISOString().slice(0, 10);
        const answer = await this.discoveryRepo.getIcebreakerAnswer(receiverId, day);
        const mine = await this.discoveryRepo.getIcebreakerAnswer(senderId, day);
        if (replyContext.id !== day || !answer || !mine) throw new AppError("context_unavailable", 409);
        context = { type: "ICEBREAKER", id: day, text: answer.answer };
      } else if (replyContext.type === "INTEREST") {
        const interest = profile.profile?.interests.find(value => value.toLowerCase() === replyContext.text?.toLowerCase());
        if (!interest) throw new AppError("context_unavailable", 409);
        context = { type: "INTEREST", id: interest, text: interest };
      } else if (replyContext.type === "VIBE" || replyContext.type === "NOTE") {
        const target = profile.profile;
        if (!target?.upFor || !target.upForUntil || target.upForUntil <= now) throw new AppError("context_unavailable", 409);
        context = { type: "VIBE", id: "current", text: target.upFor };
      } else {
        const prompts = profile.profile?.prompts;
        const prompt = Array.isArray(prompts) ? prompts.find(p => p && typeof p === "object" && !Array.isArray(p) && p.id === replyContext.id) : null;
        if (!prompt || typeof prompt !== "object" || Array.isArray(prompt) || typeof prompt.answer !== "string") throw new AppError("context_unavailable", 409);
        context = { type: "PROMPT", id: replyContext.id, text: prompt.answer };
      }
    }

    // BR-27: Check if the receiver has already sent a pending Hello to the sender!
    const incomingPending = await this.repo.findPendingHello(receiverId, senderId);
    if (incomingPending) {
      // Mutual interest! Accept the incoming hello atomically.
      const result = await this.repo.acceptHello(incomingPending.id, senderId, now);
      return {
        hello: {
          id: incomingPending.id,
          status: "ACCEPTED" as const,
          expiresAt: incomingPending.expiresAt.toISOString(),
        },
        connected: true,
        connectionId: result.connection.id,
      };
    }

    // BR-21: Check if already have a pending Hello sent
    const existingPending = await this.repo.findPendingHello(senderId, receiverId);
    if (existingPending) {
      throw new AppError("hello_already_pending", 409);
    }

    // BR-28: Daily send cap
    const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const sentToday = await this.repo.countHellosSentSince(senderId, oneDayAgo);
    if (sentToday >= this.DAILY_SEND_CAP) {
      throw new AppError("daily_cap_reached", 429, 86400);
    }

    // Create hello
    const id = uuid();
    const expiresAt = new Date(now.getTime() + this.HELLO_EXPIRY_MS);
    const hello = await this.repo.createHello({
      id,
      senderId,
      receiverId,
      message: input.message.trim(),
      ...(context ? { context } : {}),
      expiresAt,
      createdAt: now,
    });

    return {
      hello: {
        id: hello.id,
        status: hello.status,
        expiresAt: hello.expiresAt.toISOString(),
      },
      connected: false,
    };
  }

  async acceptHello(userId: string, helloId: string) {
    const now = this.clock.now();
    const result = await this.repo.acceptHello(helloId, userId, now);
    return {
      hello: {
        id: result.hello.id,
        status: "ACCEPTED" as const,
        expiresAt: result.hello.expiresAt.toISOString(),
      },
      connected: true,
      connectionId: result.connection.id,
      conversationId: result.conversation.id,
    };
  }

  async declineHello(userId: string, helloId: string) {
    const now = this.clock.now();
    await this.repo.declineHello(helloId, userId, now);
    return { ok: true as const };
  }

  async withdrawHello(userId: string, helloId: string) {
    const now = this.clock.now();
    await this.repo.withdrawHello(helloId, userId, now);
    return { ok: true as const };
  }

  async getHello(userId: string, helloId: string) {
    const hello = await this.repo.getHelloById(helloId);
    if (!hello) throw new AppError("not_found", 404);
    if (hello.senderId !== userId && hello.receiverId !== userId) {
      throw new AppError("forbidden", 403);
    }
    return {
      id: hello.id,
      senderId: hello.senderId,
      receiverId: hello.receiverId,
      message: hello.message,
      context: hello.context as { type: "PROMPT" | "PHOTO"; id: string; text: string } | null,
      status: hello.status,
      expiresAt: hello.expiresAt.toISOString(),
      createdAt: hello.createdAt.toISOString(),
      sender: {
        id: hello.sender.id,
        displayName: hello.sender.profile?.displayName ?? "User",
        photoUrl: hello.sender.photos[0]?.url ?? null,
      },
      receiver: {
        id: hello.receiver.id,
        displayName: hello.receiver.profile?.displayName ?? "User",
        photoUrl: hello.receiver.photos[0]?.url ?? null,
      },
    };
  }

  async listHellos(userId: string, query: z.infer<typeof listHellosQuerySchema>) {
    const { items, nextCursor } = await this.repo.listHellos({
      userId,
      box: query.box,
      status: query.status,
      cursor: query.cursor,
      limit: query.limit,
    });

    return {
      items: items.map((h) => ({
        id: h.id,
        senderId: h.senderId,
        receiverId: h.receiverId,
        message: h.message,
        context: h.context as { type: "PROMPT" | "PHOTO"; id: string; text: string } | null,
        status: h.status,
        expiresAt: h.expiresAt.toISOString(),
        createdAt: h.createdAt.toISOString(),
        sender: {
          id: h.sender.id,
          displayName: h.sender.profile?.displayName ?? "User",
          photoUrl: h.sender.photos[0]?.url ?? null,
        },
        receiver: {
          id: h.receiver.id,
          displayName: h.receiver.profile?.displayName ?? "User",
          photoUrl: h.receiver.photos[0]?.url ?? null,
        },
      })),
      nextCursor,
    };
  }

  async listConnections(userId: string) {
    return this.repo.listConnections(userId);
  }

  async disconnect(userId: string, connectionId: string) {
    await this.repo.disconnect(userId, connectionId);
    return { ok: true as const };
  }
}
