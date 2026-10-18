import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import type { ChatRepository } from "./chat.repo.js";
import type { SafetyRepository } from "../safety/safety.repo.js";
import type { sendMessageSchema, ackSchema, muteSchema } from "./chat.schemas.js";
import type { z } from "zod";

export class ChatService {
  constructor(
    private readonly repo: ChatRepository,
    private readonly safetyRepo: SafetyRepository,
    private readonly clock: Clock,
  ) {}

  async listConversations(userId: string) {
    const blockedIds = await this.safetyRepo.getBlockedUserIds(userId);
    return this.repo.listConversationsForUser(userId, blockedIds);
  }

  async getConversationDetail(conversationId: string, userId: string) {
    const conv = await this.repo.getConversationForUser(conversationId, userId);
    const participants: { id: string; displayName: string; photoUrl: string | null }[] = [];

    let title = "";
    if (conv.type === "DM" && conv.connection) {
      const partner =
        conv.connection.userLowId === userId
          ? conv.connection.userHigh
          : conv.connection.userLow;
      title = partner.profile?.displayName ?? "User";
      participants.push({
        id: partner.id,
        displayName: title,
        photoUrl: partner.photos[0]?.url ?? null,
      });
    } else if (conv.type === "POOL" && conv.pool) {
      title = conv.pool.title;
      for (const m of conv.pool.members) {
        participants.push({
          id: m.user.id,
          displayName: m.user.profile?.displayName ?? "Member",
          photoUrl: m.user.photos[0]?.url ?? null,
        });
      }
    }

    return {
      id: conv.id,
      type: conv.type as "DM" | "POOL",
      title,
      poolId: conv.poolId,
      isMuted: conv.mutedBy.includes(userId),
      participants,
    };
  }

  async listMessages(
    conversationId: string,
    userId: string,
    query: { before?: string | undefined; after?: string | undefined; limit?: number | undefined },
  ) {
    // Check membership / access
    await this.repo.getConversationForUser(conversationId, userId);
    const blockedIds = await this.safetyRepo.getBlockedUserIds(userId);

    return this.repo.listMessages({
      conversationId,
      userId,
      blockedIds,
      before: query.before,
      after: query.after,
      limit: Math.min(query.limit ?? 50, 100),
    });
  }

  async sendMessage(
    conversationId: string,
    userId: string,
    input: z.infer<typeof sendMessageSchema>,
  ) {
    // BR-30: Verify conversation membership
    await this.repo.getConversationForUser(conversationId, userId);

    const now = this.clock.now();
    const msg = await this.repo.sendMessage({
      conversationId,
      senderId: userId,
      clientMsgId: input.clientMsgId,
      body: input.body.trim(),
      now,
    });

    return {
      id: msg.id,
      conversationId: msg.conversationId,
      senderId: msg.senderId,
      clientMsgId: msg.clientMsgId,
      body: msg.body,
      deliveredAt: msg.deliveredAt?.toISOString() ?? null,
      deletedAt: msg.deletedAt?.toISOString() ?? null,
      createdAt: msg.createdAt.toISOString(),
    };
  }

  async deleteMessage(messageId: string, userId: string) {
    const now = this.clock.now();
    await this.repo.deleteMessage(messageId, userId, now);
    return { ok: true as const };
  }

  async ackDelivery(
    conversationId: string,
    userId: string,
    input: z.infer<typeof ackSchema>,
  ) {
    await this.repo.getConversationForUser(conversationId, userId);
    const now = this.clock.now();
    await this.repo.ackDelivery(conversationId, userId, input.lastMessageId, now);
    return { ok: true as const };
  }

  async setMute(
    conversationId: string,
    userId: string,
    input: z.infer<typeof muteSchema>,
  ) {
    await this.repo.getConversationForUser(conversationId, userId);
    await this.repo.setMute(conversationId, userId, input.muted);
    return { ok: true as const };
  }
}
