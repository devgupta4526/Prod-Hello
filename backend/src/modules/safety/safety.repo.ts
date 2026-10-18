import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";

export class SafetyRepository {
  constructor(private readonly db: Database) {}

  async blockUser(blockerId: string, blockedId: string, now: Date) {
    if (blockerId === blockedId) {
      throw new AppError("cannot_block_self", 400);
    }
    const target = await this.db.user.findUnique({ where: { id: blockedId } });
    if (!target) throw new AppError("not_found", 404);

    return this.db.$transaction(async (tx) => {
      const existing = await tx.block.findUnique({
        where: { blockerId_blockedId: { blockerId, blockedId } },
      });
      if (existing) return existing;

      // Cancel any pending Hellos between the two users
      await tx.hello.updateMany({
        where: {
          OR: [
            { senderId: blockerId, receiverId: blockedId },
            { senderId: blockedId, receiverId: blockerId },
          ],
          status: "PENDING",
        },
        data: { status: "CANCELLED_BY_BLOCK", respondedAt: now },
      });

      return tx.block.create({
        data: {
          id: uuid(),
          blockerId,
          blockedId,
          createdAt: now,
        },
      });
    });
  }

  async unblockUser(blockerId: string, blockedId: string) {
    const existing = await this.db.block.findUnique({
      where: { blockerId_blockedId: { blockerId, blockedId } },
    });
    if (!existing) return;
    await this.db.block.delete({
      where: { blockerId_blockedId: { blockerId, blockedId } },
    });
  }

  async listBlockedUsers(userId: string) {
    const blocks = await this.db.block.findMany({
      where: { blockerId: userId },
      include: {
        blocked: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
      orderBy: { createdAt: "desc" },
    });
    return blocks.map((b) => ({
      userId: b.blockedId,
      displayName: b.blocked.profile?.displayName ?? "User",
      photoUrl: b.blocked.photos[0]?.url ?? null,
      blockedAt: b.createdAt.toISOString(),
    }));
  }

  async getBlockedUserIds(userId: string): Promise<Set<string>> {
    const blocks = await this.db.block.findMany({
      where: {
        OR: [{ blockerId: userId }, { blockedId: userId }],
      },
      select: { blockerId: true, blockedId: true },
    });
    const set = new Set<string>();
    for (const b of blocks) {
      if (b.blockerId === userId) set.add(b.blockedId);
      if (b.blockedId === userId) set.add(b.blockerId);
    }
    return set;
  }

  async isBlockedPair(userA: string, userB: string): Promise<boolean> {
    const count = await this.db.block.count({
      where: {
        OR: [
          { blockerId: userA, blockedId: userB },
          { blockerId: userB, blockedId: userA },
        ],
      },
    });
    return count > 0;
  }

  async createReport(input: {
    reporterId: string;
    targetType: string;
    targetId: string;
    targetUserId?: string | undefined;
    reason: string;
    details?: string | undefined;
    evidence?: unknown;
    now: Date;
  }) {
    return this.db.report.create({
      data: {
        id: uuid(),
        reporterId: input.reporterId,
        targetType: input.targetType,
        targetId: input.targetId,
        targetUserId: input.targetUserId ?? null,
        reason: input.reason,
        details: input.details ?? null,
        ...(input.evidence ? { evidence: input.evidence as object } : {}),
        createdAt: input.now,
      },
    });
  }

  async createAuditLog(input: {
    actorId?: string | undefined;
    action: string;
    entityType: string;
    entityId?: string | undefined;
    meta?: unknown;
    ipHash?: string | undefined;
    now: Date;
  }) {
    return this.db.auditLog.create({
      data: {
        id: uuid(),
        actorId: input.actorId ?? null,
        action: input.action,
        entityType: input.entityType,
        entityId: input.entityId ?? null,
        ...(input.meta ? { meta: input.meta as object } : {}),
        ipHash: input.ipHash ?? null,
        createdAt: input.now,
      },
    });
  }
}
