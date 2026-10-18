import { v7 as uuid } from "uuid";
import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import type { PoolsRepository } from "./pools.repo.js";
import type { SafetyRepository } from "../safety/safety.repo.js";
import type { DiscoveryRepository } from "../discovery/discovery.repo.js";
import type {
  createPoolSchema,
  updatePoolSchema,
  knockSchema,
} from "./pools.schemas.js";
import type { z } from "zod";

export class PoolsService {
  constructor(
    private readonly repo: PoolsRepository,
    private readonly safetyRepo: SafetyRepository,
    private readonly discoveryRepo: DiscoveryRepository,
    private readonly clock: Clock,
  ) {}

  async createPool(
    organizerId: string,
    input: z.infer<typeof createPoolSchema>,
  ) {
    const now = this.clock.now();
    const startsAt = new Date(input.startsAt);
    if (startsAt <= now) {
      throw new AppError("starts_at_must_be_future", 400);
    }
    const endsAt = input.endsAt ? new Date(input.endsAt) : undefined;
    if (endsAt && endsAt <= startsAt) {
      throw new AppError("ends_at_must_be_after_starts_at", 400);
    }

    const pool = await this.repo.createPool({
      id: uuid(),
      organizerId,
      title: input.title,
      description: input.description,
      category: input.category,
      startsAt,
      endsAt,
      capacity: input.capacity,
      venueName: input.venueName,
      venueLat: input.venueLat,
      venueLng: input.venueLng,
      areaLabel: input.areaLabel,
      now,
    });

    return this.getPoolDetail(organizerId, pool.id);
  }

  async getPoolDetail(userId: string, poolId: string) {
    const pool = await this.repo.getPool(poolId);
    if (!pool) throw new AppError("not_found", 404);

    // BR-46 / BR-11: Block check
    const isBlocked = await this.safetyRepo.isBlockedPair(
      userId,
      pool.organizerId,
    );
    if (isBlocked) throw new AppError("not_found", 404);

    const isMember = pool.members.some((m) => m.userId === userId);
    const isOrganizer = pool.organizerId === userId;

    let myKnockStatus: any = null;
    if (!isMember) {
      const knock = await this.repo.listKnocks(poolId, pool.organizerId);
      const myKnock = knock.find((k) => k.userId === userId);
      if (myKnock) myKnockStatus = myKnock.status;
    }

    return {
      id: pool.id,
      title: pool.title,
      description: pool.description,
      category: pool.category,
      startsAt: pool.startsAt.toISOString(),
      endsAt: pool.endsAt?.toISOString() ?? null,
      capacity: pool.capacity,
      memberCount: pool.members.length,
      status: pool.status,
      areaLabel: pool.areaLabel,
      // BR-45: Exact venue ONLY to accepted members
      venueName: isMember ? pool.venueName : null,
      venueLat: isMember ? pool.venueLat : null,
      venueLng: isMember ? pool.venueLng : null,
      isMember,
      isOrganizer,
      conversationId: isMember ? pool.conversation?.id ?? null : null,
      myKnockStatus,
      organizer: {
        id: pool.organizer.id,
        displayName: pool.organizer.profile?.displayName ?? "Organizer",
        photoUrl: pool.organizer.photos[0]?.url ?? null,
      },
    };
  }

  async updatePool(
    organizerId: string,
    poolId: string,
    input: z.infer<typeof updatePoolSchema>,
  ) {
    const data: any = { ...input };
    if (input.startsAt) data.startsAt = new Date(input.startsAt);
    if (input.endsAt) data.endsAt = new Date(input.endsAt);

    await this.repo.updatePool(poolId, organizerId, data);
    return this.getPoolDetail(organizerId, poolId);
  }

  async cancelPool(organizerId: string, poolId: string) {
    await this.repo.cancelPool(poolId, organizerId);
    return { ok: true as const };
  }

  async knock(
    userId: string,
    poolId: string,
    input: z.infer<typeof knockSchema>,
  ) {
    const pool = await this.repo.getPool(poolId);
    if (!pool) throw new AppError("not_found", 404);

    // BR-46: Block check
    const isBlocked = await this.safetyRepo.isBlockedPair(
      userId,
      pool.organizerId,
    );
    if (isBlocked) throw new AppError("forbidden", 403);

    const now = this.clock.now();
    const knock = await this.repo.createKnock(
      poolId,
      userId,
      input.message,
      now,
    );
    return {
      id: knock.id,
      poolId: knock.poolId,
      userId: knock.userId,
      message: knock.message,
      status: knock.status,
      createdAt: knock.createdAt.toISOString(),
    };
  }

  async withdrawKnock(userId: string, poolId: string) {
    const now = this.clock.now();
    await this.repo.withdrawKnock(poolId, userId, now);
    return { ok: true as const };
  }

  async listKnocks(organizerId: string, poolId: string) {
    const knocks = await this.repo.listKnocks(poolId, organizerId);
    return knocks.map((k) => ({
      id: k.id,
      poolId: k.poolId,
      userId: k.userId,
      message: k.message,
      status: k.status,
      createdAt: k.createdAt.toISOString(),
      user: {
        id: k.user.id,
        displayName: k.user.profile?.displayName ?? "User",
        photoUrl: k.user.photos[0]?.url ?? null,
      },
    }));
  }

  async acceptKnock(organizerId: string, poolId: string, knockId: string) {
    const now = this.clock.now();
    await this.repo.acceptKnock(poolId, knockId, organizerId, now);
    return { ok: true as const };
  }

  async declineKnock(organizerId: string, poolId: string, knockId: string) {
    const now = this.clock.now();
    await this.repo.declineKnock(poolId, knockId, organizerId, now);
    return { ok: true as const };
  }

  async listMembers(userId: string, poolId: string) {
    // Only members can view member list
    const pool = await this.repo.getPool(poolId);
    if (!pool) throw new AppError("not_found", 404);
    const isMember = pool.members.some((m) => m.userId === userId);
    if (!isMember) throw new AppError("forbidden", 403);

    const members = await this.repo.listMembers(poolId);
    return members.map((m) => ({
      userId: m.userId,
      displayName: m.user.profile?.displayName ?? "Member",
      photoUrl: m.user.photos[0]?.url ?? null,
      joinedAt: m.joinedAt.toISOString(),
    }));
  }

  async leavePool(userId: string, poolId: string) {
    const now = this.clock.now();
    await this.repo.leavePool(poolId, userId, now);
    return { ok: true as const };
  }

  async removeMember(
    organizerId: string,
    poolId: string,
    targetUserId: string,
  ) {
    const now = this.clock.now();
    await this.repo.removeMember(poolId, targetUserId, organizerId, now);
    return { ok: true as const };
  }

  async listNearbyPools(userId: string, category?: string) {
    const now = this.clock.now();
    const pools = await this.repo.listNearbyPools(category, now);
    const blockedIds = await this.safetyRepo.getBlockedUserIds(userId);

    const items = [];
    for (const pool of pools) {
      if (blockedIds.has(pool.organizerId)) continue; // BR-46

      const isMember = pool.members.some((m) => m.userId === userId);
      const isOrganizer = pool.organizerId === userId;

      items.push({
        id: pool.id,
        title: pool.title,
        description: pool.description,
        category: pool.category,
        startsAt: pool.startsAt.toISOString(),
        endsAt: pool.endsAt?.toISOString() ?? null,
        capacity: pool.capacity,
        memberCount: pool.members.length,
        status: pool.status,
        areaLabel: pool.areaLabel,
        // BR-45: Exact venue ONLY for accepted members
        venueName: isMember ? pool.venueName : null,
        isMember,
        isOrganizer,
        organizer: {
          id: pool.organizer.id,
          displayName: pool.organizer.profile?.displayName ?? "Organizer",
          photoUrl: pool.organizer.photos[0]?.url ?? null,
        },
      });
    }

    return items;
  }

  async listMyPools(
    userId: string,
    role?: "member" | "organizer",
    when?: "upcoming" | "past",
  ) {
    const now = this.clock.now();
    const pools = await this.repo.listMyPools(userId, role, when, now);

    return pools.map((pool) => {
      const isMember = pool.members.some((m) => m.userId === userId);
      const isOrganizer = pool.organizerId === userId;

      return {
        id: pool.id,
        title: pool.title,
        description: pool.description,
        category: pool.category,
        startsAt: pool.startsAt.toISOString(),
        endsAt: pool.endsAt?.toISOString() ?? null,
        capacity: pool.capacity,
        memberCount: pool.members.length,
        status: pool.status,
        areaLabel: pool.areaLabel,
        venueName: pool.venueName,
        isMember,
        isOrganizer,
        organizer: {
          id: pool.organizer.id,
          displayName: pool.organizer.profile?.displayName ?? "Organizer",
          photoUrl: pool.organizer.photos[0]?.url ?? null,
        },
      };
    });
  }
}
