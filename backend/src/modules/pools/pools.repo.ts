import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { AppError } from "../../lib/app-error.js";
import { computeCellId } from "../../lib/location.js";
import type { KnockStatus, PoolStatus } from "../../generated/prisma/client.js";

export class PoolsRepository {
  constructor(private readonly db: Database) {}

  async createPool(input: {
    id: string;
    organizerId: string;
    title: string;
    description: string;
    category: string;
    startsAt: Date;
    endsAt?: Date | undefined;
    capacity: number;
    venueName: string;
    venueLat: number;
    venueLng: number;
    areaLabel: string;
    now: Date;
  }) {
    const areaCell = computeCellId(input.venueLat, input.venueLng);

    return this.db.$transaction(async (tx) => {
      // 1. Create Pool
      const pool = await tx.pool.create({
        data: {
          id: input.id,
          organizerId: input.organizerId,
          title: input.title,
          description: input.description,
          category: input.category,
          startsAt: input.startsAt,
          endsAt: input.endsAt ?? null,
          capacity: input.capacity,
          status: "OPEN",
          venueName: input.venueName,
          venueLat: input.venueLat,
          venueLng: input.venueLng,
          areaLabel: input.areaLabel,
          areaCell,
          createdAt: input.now,
        },
      });

      // 2. BR-40: Creator is first member
      await tx.poolMember.create({
        data: {
          id: uuid(),
          poolId: pool.id,
          userId: input.organizerId,
          joinedAt: input.now,
        },
      });

      // 3. Pool group conversation
      await tx.conversation.create({
        data: {
          id: uuid(),
          type: "POOL",
          poolId: pool.id,
          createdAt: input.now,
        },
      });

      return pool;
    });
  }

  async getPool(poolId: string) {
    return this.db.pool.findUnique({
      where: { id: poolId },
      include: {
        organizer: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        members: {
          where: { leftAt: null },
          include: {
            user: {
              include: { profile: true, photos: { orderBy: { position: "asc" } } },
            },
          },
        },
        conversation: true,
      },
    });
  }

  async updatePool(poolId: string, organizerId: string, data: Partial<{
    title: string;
    description: string;
    category: string;
    startsAt: Date;
    endsAt: Date;
    capacity: number;
    venueName: string;
    venueLat: number;
    venueLng: number;
    areaLabel: string;
  }>) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);

    const updateData: Record<string, unknown> = { ...data };
    if (data.venueLat !== undefined && data.venueLng !== undefined) {
      updateData.areaCell = computeCellId(data.venueLat, data.venueLng);
    }

    return this.db.pool.update({
      where: { id: poolId },
      data: updateData,
    });
  }

  async cancelPool(poolId: string, organizerId: string) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);

    return this.db.pool.update({
      where: { id: poolId },
      data: { status: "CANCELLED" },
    });
  }

  async createKnock(poolId: string, userId: string, message: string | undefined, now: Date) {
    const pool = await this.db.pool.findUnique({
      where: { id: poolId },
      include: { members: { where: { leftAt: null } } },
    });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.status !== "OPEN") throw new AppError("pool_not_open", 409);
    if (pool.startsAt <= now) throw new AppError("pool_already_started", 409); // BR-44

    // BR-42: No duplicate membership
    const isMember = pool.members.some((m) => m.userId === userId);
    if (isMember) throw new AppError("already_member", 409);

    // BR-42: No duplicate pending Knock
    const existing = await this.db.poolKnock.findFirst({
      where: { poolId, userId, status: "PENDING" },
    });
    if (existing) throw new AppError("knock_already_pending", 409);

    const knock = await this.db.poolKnock.create({
      data: {
        id: uuid(),
        poolId,
        userId,
        message: message ?? null,
        status: "PENDING",
        createdAt: now,
      },
    });

    // Notify organizer
    await this.db.notification.create({
      data: {
        id: uuid(),
        userId: pool.organizerId,
        type: "knock.received",
        payload: { poolId, knockId: knock.id, knockerId: userId },
        createdAt: now,
      },
    });

    return knock;
  }

  async withdrawKnock(poolId: string, userId: string, now: Date) {
    const knock = await this.db.poolKnock.findFirst({
      where: { poolId, userId, status: "PENDING" },
    });
    if (!knock) throw new AppError("not_found", 404);

    await this.db.poolKnock.update({
      where: { id: knock.id },
      data: { status: "WITHDRAWN", decidedAt: now },
    });
  }

  async listKnocks(poolId: string, organizerId: string) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);

    return this.db.poolKnock.findMany({
      where: { poolId, status: "PENDING" },
      orderBy: { createdAt: "desc" },
      include: {
        user: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
    });
  }

  async acceptKnock(poolId: string, knockId: string, organizerId: string, now: Date) {
    return this.db.$transaction(async (tx) => {
      const pool = await tx.pool.findUnique({
        where: { id: poolId },
        include: { members: { where: { leftAt: null } } },
      });
      if (!pool) throw new AppError("not_found", 404);
      if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);

      const knock = await tx.poolKnock.findUnique({ where: { id: knockId } });
      if (!knock || knock.poolId !== poolId) throw new AppError("not_found", 404);
      if (knock.status !== "PENDING") throw new AppError("already_decided", 409);

      // BR-41: Check capacity
      if (pool.members.length >= pool.capacity) {
        throw new AppError("pool_full", 409);
      }

      // Mark knock accepted
      await tx.poolKnock.update({
        where: { id: knockId },
        data: { status: "ACCEPTED", decidedAt: now },
      });

      // Add pool member
      await tx.poolMember.create({
        data: {
          id: uuid(),
          poolId,
          userId: knock.userId,
          joinedAt: now,
        },
      });

      // If capacity reached, mark FULL
      if (pool.members.length + 1 >= pool.capacity) {
        await tx.pool.update({
          where: { id: poolId },
          data: { status: "FULL" },
        });
      }

      // Notify accepted user
      await tx.notification.create({
        data: {
          id: uuid(),
          userId: knock.userId,
          type: "knock.accepted",
          payload: { poolId, knockId },
          createdAt: now,
        },
      });

      return knock;
    });
  }

  async declineKnock(poolId: string, knockId: string, organizerId: string, now: Date) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);

    const knock = await this.db.poolKnock.findUnique({ where: { id: knockId } });
    if (!knock || knock.poolId !== poolId) throw new AppError("not_found", 404);
    if (knock.status !== "PENDING") return;

    await this.db.poolKnock.update({
      where: { id: knockId },
      data: { status: "DECLINED", decidedAt: now },
    });
  }

  async listMembers(poolId: string) {
    return this.db.poolMember.findMany({
      where: { poolId, leftAt: null },
      include: {
        user: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
      },
      orderBy: { joinedAt: "asc" },
    });
  }

  async leavePool(poolId: string, userId: string, now: Date) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId === userId) {
      throw new AppError("organizer_cannot_leave", 400);
    }

    const member = await this.db.poolMember.findUnique({
      where: { poolId_userId: { poolId, userId } },
    });
    if (!member || member.leftAt !== null) return;

    await this.db.$transaction(async (tx) => {
      await tx.poolMember.update({
        where: { poolId_userId: { poolId, userId } },
        data: { leftAt: now },
      });

      if (pool.status === "FULL") {
        await tx.pool.update({
          where: { id: poolId },
          data: { status: "OPEN" },
        });
      }
    });
  }

  async removeMember(poolId: string, memberId: string, organizerId: string, now: Date) {
    const pool = await this.db.pool.findUnique({ where: { id: poolId } });
    if (!pool) throw new AppError("not_found", 404);
    if (pool.organizerId !== organizerId) throw new AppError("forbidden", 403);
    if (memberId === organizerId) throw new AppError("cannot_remove_self", 400);

    await this.leavePool(poolId, memberId, now);
  }

  async listNearbyPools(category?: string, now?: Date) {
    return this.db.pool.findMany({
      where: {
        status: { in: ["OPEN", "FULL"] },
        ...(now ? { startsAt: { gt: now } } : {}),
        ...(category ? { category } : {}),
      },
      include: {
        organizer: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        members: { where: { leftAt: null } },
      },
      orderBy: { startsAt: "asc" },
      take: 50,
    });
  }

  async listMyPools(userId: string, role?: "member" | "organizer", when?: "upcoming" | "past", now: Date = new Date()) {
    const where: Record<string, unknown> = {};
    if (role === "organizer") {
      where.organizerId = userId;
    } else {
      where.members = { some: { userId, leftAt: null } };
    }

    if (when === "upcoming") {
      where.startsAt = { gte: now };
    } else if (when === "past") {
      where.startsAt = { lt: now };
    }

    return this.db.pool.findMany({
      where,
      include: {
        organizer: {
          include: { profile: true, photos: { orderBy: { position: "asc" } } },
        },
        members: { where: { leftAt: null } },
      },
      orderBy: { startsAt: when === "past" ? "desc" : "asc" },
    });
  }
}
