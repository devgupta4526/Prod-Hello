import type { Database } from "../../lib/database.js";
import { computeCellId } from "../../lib/location.js";
import type { Availability } from "../../generated/prisma/client.js";

export class DiscoveryRepository {
  constructor(private readonly db: Database) {}

  async upsertPresence(input: {
    userId: string;
    availability: Availability;
    lat: number;
    lng: number;
    accuracyM?: number | undefined;
    now: Date;
    ttlMs: number;
  }) {
    if (input.availability === "OFF") {
      await this.deletePresence(input.userId);
      return null;
    }

    const cellId = computeCellId(input.lat, input.lng);
    const expiresAt = new Date(input.now.getTime() + input.ttlMs);

    return this.db.presence.upsert({
      where: { userId: input.userId },
      create: {
        userId: input.userId,
        availability: input.availability,
        lat: input.lat,
        lng: input.lng,
        accuracyM: input.accuracyM ?? null,
        cellId,
        updatedAt: input.now,
        expiresAt,
      },
      update: {
        availability: input.availability,
        lat: input.lat,
        lng: input.lng,
        accuracyM: input.accuracyM ?? null,
        cellId,
        updatedAt: input.now,
        expiresAt,
      },
    });
  }

  async deletePresence(userId: string) {
    try {
      await this.db.presence.delete({ where: { userId } });
    } catch {
      // Ignore if not found
    }
  }

  async getPresence(userId: string) {
    return this.db.presence.findUnique({ where: { userId } });
  }

  async findEligibleActivePresences(
    viewerId: string,
    blockedIds: Set<string>,
    now: Date,
  ) {
    // BR-03, BR-11, BR-13, §11.5
    const presences = await this.db.presence.findMany({
      where: {
        userId: {
          not: viewerId,
          notIn: Array.from(blockedIds),
        },
        availability: { not: "OFF" },
        expiresAt: { gt: now },
        user: {
          status: "ACTIVE",
          adultVerifiedAt: { not: null },
          guidelinesAcceptedAt: { not: null },
          profile: { isNot: null },
        },
      },
      include: {
        user: {
          include: {
            profile: true,
            photos: { orderBy: { position: "asc" } },
          },
        },
      },
    });
    return presences;
  }

  async getPublicProfile(targetUserId: string) {
    return this.db.user.findUnique({
      where: { id: targetUserId },
      include: {
        profile: true,
        photos: { orderBy: { position: "asc" } },
        presence: true,
      },
    });
  }
}
