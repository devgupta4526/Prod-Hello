import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";
import type { HelloStatus } from "../../generated/prisma/client.js";

export class HelloRepository {
  constructor(private readonly db: Database) {}

  async findPendingHello(senderId: string, receiverId: string) {
    return this.db.hello.findFirst({
      where: {
        senderId,
        receiverId,
        status: "PENDING",
      },
    });
  }

  async countHellosSentSince(senderId: string, since: Date) {
    return this.db.hello.count({
      where: {
        senderId,
        createdAt: { gte: since },
      },
    });
  }

  async createHello(data: {
    id: string;
    senderId: string;
    receiverId: string;
    message: string;
    context?: { type: string; id: string; text: string };
    expiresAt: Date;
    createdAt: Date;
  }) {
    return this.db.hello.create({ data });
  }

  async getHelloById(id: string) {
    return this.db.hello.findUnique({
      where: { id },
      include: {
        sender: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        receiver: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
    });
  }

  async acceptHello(helloId: string, receiverId: string, now: Date) {
    return this.db.$transaction(async (tx) => {
      const hello = await tx.hello.findUnique({ where: { id: helloId } });
      if (!hello) throw new AppError("not_found", 404);
      if (hello.receiverId !== receiverId) throw new AppError("forbidden", 403);
      if (hello.status !== "PENDING") throw new AppError("already_responded", 409);
      if (hello.expiresAt <= now) {
        await tx.hello.update({
          where: { id: helloId },
          data: { status: "EXPIRED" },
        });
        throw new AppError("hello_expired", 410);
      }

      // Mark accepted
      await tx.hello.update({
        where: { id: helloId },
        data: { status: "ACCEPTED", respondedAt: now },
      });

      // BR-24: userLowId < userHighId
      const userLowId = hello.senderId < hello.receiverId ? hello.senderId : hello.receiverId;
      const userHighId = hello.senderId < hello.receiverId ? hello.receiverId : hello.senderId;

      let connection = await tx.connection.findUnique({
        where: { userLowId_userHighId: { userLowId, userHighId } },
      });

      if (!connection) {
        connection = await tx.connection.create({
          data: {
            id: uuid(),
            userLowId,
            userHighId,
            createdAt: now,
          },
        });
      }

      let conversation = await tx.conversation.findUnique({
        where: { connectionId: connection.id },
      });

      if (!conversation) {
        conversation = await tx.conversation.create({
          data: {
            id: uuid(),
            type: "DM",
            connectionId: connection.id,
            createdAt: now,
          },
        });
      }

      // BR-60: Persist notification before push
      await tx.notification.create({
        data: {
          id: uuid(),
          userId: hello.senderId,
          type: "hello.accepted",
          payload: {
            helloId: hello.id,
            partnerId: receiverId,
            conversationId: conversation.id,
          },
          createdAt: now,
        },
      });

      return { hello, connection, conversation };
    });
  }

  async declineHello(helloId: string, receiverId: string, now: Date) {
    const hello = await this.db.hello.findUnique({ where: { id: helloId } });
    if (!hello) throw new AppError("not_found", 404);
    if (hello.receiverId !== receiverId) throw new AppError("forbidden", 403);
    if (hello.status !== "PENDING") return;

    // BR-25: Decline is silent to sender
    await this.db.hello.update({
      where: { id: helloId },
      data: { status: "DECLINED", respondedAt: now },
    });
  }

  async withdrawHello(helloId: string, senderId: string, now: Date) {
    const hello = await this.db.hello.findUnique({ where: { id: helloId } });
    if (!hello) throw new AppError("not_found", 404);
    if (hello.senderId !== senderId) throw new AppError("forbidden", 403);
    if (hello.status !== "PENDING") throw new AppError("already_responded", 409);

    await this.db.hello.update({
      where: { id: helloId },
      data: { status: "WITHDRAWN", respondedAt: now },
    });
  }

  async listHellos(input: {
    userId: string;
    box: "received" | "sent";
    status?: HelloStatus | undefined;
    cursor?: string | undefined;
    limit: number;
  }) {
    const where: Record<string, unknown> =
      input.box === "received"
        ? { receiverId: input.userId }
        : { senderId: input.userId };

    if (input.status) {
      where.status = input.status;
    }

    const hellos = await this.db.hello.findMany({
      where,
      orderBy: { createdAt: "desc" },
      take: input.limit + 1,
      ...(input.cursor ? { cursor: { id: input.cursor }, skip: 1 } : {}),
      include: {
        sender: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        receiver: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
    });

    const hasMore = hellos.length > input.limit;
    const items = hasMore ? hellos.slice(0, input.limit) : hellos;
    const nextCursor = hasMore ? items[items.length - 1]?.id ?? null : null;

    return { items, nextCursor };
  }

  async listConnections(userId: string) {
    const connections = await this.db.connection.findMany({
      where: {
        OR: [{ userLowId: userId }, { userHighId: userId }],
      },
      include: {
        userLow: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        userHigh: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        conversation: true,
      },
      orderBy: { createdAt: "desc" },
    });

    return connections.map((c) => {
      const partner = c.userLowId === userId ? c.userHigh : c.userLow;
      return {
        id: c.id,
        createdAt: c.createdAt.toISOString(),
        partner: {
          id: partner.id,
          displayName: partner.profile?.displayName ?? "User",
          photoUrl: partner.photos[0]?.url ?? null,
        },
        conversationId: c.conversation?.id ?? null,
      };
    });
  }

  async disconnect(userId: string, connectionId: string) {
    const conn = await this.db.connection.findUnique({
      where: { id: connectionId },
    });
    if (!conn) return;
    if (conn.userLowId !== userId && conn.userHighId !== userId) {
      throw new AppError("forbidden", 403);
    }
    await this.db.connection.delete({ where: { id: connectionId } });
  }
}
