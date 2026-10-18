import type { Database } from "../../lib/database.js";

export class NotificationsRepository {
  constructor(private readonly db: Database) {}

  async listNotifications(userId: string, cursor?: string, limit: number = 20) {
    const items = await this.db.notification.findMany({
      where: { userId },
      orderBy: { createdAt: "desc" },
      take: limit + 1,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
    });

    const hasMore = items.length > limit;
    const paginated = hasMore ? items.slice(0, limit) : items;
    const nextCursor = hasMore ? paginated[paginated.length - 1]?.id ?? null : null;

    return {
      items: paginated.map((n) => ({
        id: n.id,
        type: n.type,
        payload: n.payload,
        readAt: n.readAt?.toISOString() ?? null,
        createdAt: n.createdAt.toISOString(),
      })),
      nextCursor,
    };
  }

  async getUnreadCount(userId: string): Promise<number> {
    return this.db.notification.count({
      where: {
        userId,
        readAt: null,
      },
    });
  }

  async markAsRead(userId: string, ids?: string[], all?: boolean, now: Date = new Date()) {
    if (all) {
      await this.db.notification.updateMany({
        where: { userId, readAt: null },
        data: { readAt: now },
      });
    } else if (ids && ids.length > 0) {
      await this.db.notification.updateMany({
        where: { userId, id: { in: ids }, readAt: null },
        data: { readAt: now },
      });
    }
  }
}
