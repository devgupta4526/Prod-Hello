import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";

export class ChatRepository {
  constructor(private readonly db: Database) {}

  async getConversationForUser(conversationId: string, userId: string) {
    const conv = await this.db.conversation.findUnique({
      where: { id: conversationId },
      include: {
        connection: {
          include: {
            userLow: {
              include: {
                profile: true,
                photos: { orderBy: { position: "asc" } },
              },
            },
            userHigh: {
              include: {
                profile: true,
                photos: { orderBy: { position: "asc" } },
              },
            },
          },
        },
        pool: {
          include: {
            members: {
              where: { leftAt: null },
              include: {
                user: {
                  include: {
                    profile: true,
                    photos: { orderBy: { position: "asc" } },
                  },
                },
              },
            },
          },
        },
      },
    });

    if (!conv) throw new AppError("not_found", 404);

    if (conv.type === "DM") {
      if (
        !conv.connection ||
        (conv.connection.userLowId !== userId &&
          conv.connection.userHighId !== userId)
      ) {
        throw new AppError("forbidden", 403);
      }
      const partnerId =
        conv.connection.userLowId === userId
          ? conv.connection.userHighId
          : conv.connection.userLowId;
      const isBlocked = await this.db.block.findFirst({
        where: {
          OR: [
            { blockerId: userId, blockedId: partnerId },
            { blockerId: partnerId, blockedId: userId },
          ],
        },
      });
      if (isBlocked) throw new AppError("not_found", 404);
    } else if (conv.type === "POOL") {
      const isMember = conv.pool?.members.some((m) => m.userId === userId);
      if (!isMember) throw new AppError("forbidden", 403);
    }

    return conv;
  }

  async listConversationsForUser(userId: string, blockedIds: Set<string>) {
    // 1. Fetch DM conversations
    const dmConversations = await this.db.conversation.findMany({
      where: {
        type: "DM",
        connection: {
          OR: [{ userLowId: userId }, { userHighId: userId }],
        },
      },
      include: {
        connection: {
          include: {
            userLow: {
              include: {
                profile: true,
                photos: { orderBy: { position: "asc" } },
              },
            },
            userHigh: {
              include: {
                profile: true,
                photos: { orderBy: { position: "asc" } },
              },
            },
          },
        },
        messages: {
          orderBy: { createdAt: "desc" },
          take: 1,
        },
      },
    });

    // 2. Fetch Pool conversations
    const poolConversations = await this.db.conversation.findMany({
      where: {
        type: "POOL",
        pool: {
          members: {
            some: {
              userId,
              leftAt: null,
            },
          },
        },
      },
      include: {
        pool: true,
        messages: {
          orderBy: { createdAt: "desc" },
          take: 1,
        },
      },
    });

    const results = [];

    // Filter BR-33: Blocking removes the DM conversation
    for (const dm of dmConversations) {
      if (!dm.connection) continue;
      const partner =
        dm.connection.userLowId === userId
          ? dm.connection.userHigh
          : dm.connection.userLow;

      if (blockedIds.has(partner.id)) continue;

      const lastMsg = dm.messages[0] ?? null;

      results.push({
        id: dm.id,
        type: "DM" as const,
        title: partner.profile?.displayName ?? "User",
        photoUrl: partner.photos[0]?.url ?? null,
        partnerId: partner.id,
        poolId: null,
        isMuted: dm.mutedBy.includes(userId),
        lastMessage: lastMsg
          ? {
              id: lastMsg.id,
              conversationId: lastMsg.conversationId,
              senderId: lastMsg.senderId,
              clientMsgId: lastMsg.clientMsgId,
              body: lastMsg.deletedAt ? "[message deleted]" : lastMsg.body,
              deliveredAt: lastMsg.deliveredAt?.toISOString() ?? null,
              deletedAt: lastMsg.deletedAt?.toISOString() ?? null,
              createdAt: lastMsg.createdAt.toISOString(),
            }
          : null,
        createdAt: dm.createdAt.toISOString(),
      });
    }

    for (const pc of poolConversations) {
      if (!pc.pool) continue;
      const lastMsg = pc.messages[0] ?? null;

      results.push({
        id: pc.id,
        type: "POOL" as const,
        title: pc.pool.title,
        photoUrl: null,
        partnerId: null,
        poolId: pc.pool.id,
        isMuted: pc.mutedBy.includes(userId),
        lastMessage: lastMsg
          ? {
              id: lastMsg.id,
              conversationId: lastMsg.conversationId,
              senderId: lastMsg.senderId,
              clientMsgId: lastMsg.clientMsgId,
              body: lastMsg.deletedAt ? "[message deleted]" : lastMsg.body,
              deliveredAt: null,
              deletedAt: lastMsg.deletedAt?.toISOString() ?? null,
              createdAt: lastMsg.createdAt.toISOString(),
            }
          : null,
        createdAt: pc.createdAt.toISOString(),
      });
    }

    // Sort by last message createdAt or conversation createdAt
    results.sort((a, b) => {
      const timeA = a.lastMessage?.createdAt ?? a.createdAt;
      const timeB = b.lastMessage?.createdAt ?? b.createdAt;
      return new Date(timeB).getTime() - new Date(timeA).getTime();
    });

    return results;
  }

  async listMessages(input: {
    conversationId: string;
    userId: string;
    blockedIds: Set<string>;
    before?: string | undefined;
    after?: string | undefined;
    limit: number;
  }) {
    // BR-34: Messages from a user the viewer has blocked are excluded
    const messages = await this.db.message.findMany({
      where: {
        conversationId: input.conversationId,
        senderId: { notIn: Array.from(input.blockedIds) },
        ...(input.before ? { createdAt: { lt: new Date(input.before) } } : {}),
        ...(input.after ? { createdAt: { gt: new Date(input.after) } } : {}),
      },
      orderBy: { createdAt: "asc" },
      take: input.limit,
      include: {
        sender: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
    });

    return messages.map((m) => ({
      id: m.id,
      conversationId: m.conversationId,
      senderId: m.senderId,
      clientMsgId: m.clientMsgId,
      body: m.deletedAt ? "[message deleted]" : m.body,
      deliveredAt: m.deliveredAt?.toISOString() ?? null,
      deletedAt: m.deletedAt?.toISOString() ?? null,
      createdAt: m.createdAt.toISOString(),
      sender: {
        id: m.sender.id,
        displayName: m.sender.profile?.displayName ?? "User",
        photoUrl: m.sender.photos[0]?.url ?? null,
      },
    }));
  }

  async sendMessage(input: {
    conversationId: string;
    senderId: string;
    clientMsgId: string;
    body: string;
    now: Date;
  }) {
    // BR-32: Idempotent with clientMsgId
    const existing = await this.db.message.findUnique({
      where: {
        senderId_clientMsgId: {
          senderId: input.senderId,
          clientMsgId: input.clientMsgId,
        },
      },
    });
    if (existing) return existing;

    return this.db.message.create({
      data: {
        id: uuid(),
        conversationId: input.conversationId,
        senderId: input.senderId,
        clientMsgId: input.clientMsgId,
        body: input.body,
        createdAt: input.now,
      },
    });
  }

  async deleteMessage(messageId: string, senderId: string, now: Date) {
    const msg = await this.db.message.findUnique({ where: { id: messageId } });
    if (!msg) throw new AppError("not_found", 404);
    if (msg.senderId !== senderId) throw new AppError("forbidden", 403);

    // BR-32: Tombstone
    return this.db.message.update({
      where: { id: messageId },
      data: { deletedAt: now },
    });
  }

  async ackDelivery(conversationId: string, userId: string, lastMessageId: string, now: Date) {
    await this.db.message.updateMany({
      where: {
        conversationId,
        senderId: { not: userId },
        id: { lte: lastMessageId },
        deliveredAt: null,
      },
      data: { deliveredAt: now },
    });
  }

  async setMute(conversationId: string, userId: string, muted: boolean) {
    const conv = await this.db.conversation.findUnique({
      where: { id: conversationId },
    });
    if (!conv) throw new AppError("not_found", 404);

    let mutedBy = conv.mutedBy;
    if (muted && !mutedBy.includes(userId)) {
      mutedBy = [...mutedBy, userId];
    } else if (!muted && mutedBy.includes(userId)) {
      mutedBy = mutedBy.filter((id) => id !== userId);
    }

    await this.db.conversation.update({
      where: { id: conversationId },
      data: { mutedBy },
    });
  }
}
