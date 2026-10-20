import { AppError } from "../../lib/app-error.js";
import type { Clock } from "../../lib/clock.js";
import {
  haversineDistanceKm,
  toDistanceBucket,
  computeFuzzedMarker,
} from "../../lib/location.js";
import type { DiscoveryRepository } from "./discovery.repo.js";
import type { SafetyRepository } from "../safety/safety.repo.js";
import type { Environment } from "../../config/env.js";
import type {
  nearbyQuerySchema,
  presenceUpdateSchema,
} from "./discovery.schemas.js";
import type { z } from "zod";

export class DiscoveryService {
  private readonly PRESENCE_TTL_MS = 30 * 60 * 1000; // 30 minutes (BR-13)
  private readonly presenceRateLimits = new Map<string, number>();

  constructor(
    private readonly repo: DiscoveryRepository,
    private readonly safetyRepo: SafetyRepository,
    private readonly env: Environment,
    private readonly clock: Clock,
  ) {}

  async updatePresence(
    userId: string,
    input: z.infer<typeof presenceUpdateSchema>,
  ) {
    const now = this.clock.now();
    const lastUpdate = this.presenceRateLimits.get(userId) ?? 0;
    if (now.getTime() - lastUpdate < 30000) {
      // 1 update per 30s limit (§11.1)
      throw new AppError("rate_limited", 429, 30);
    }
    this.presenceRateLimits.set(userId, now.getTime());

    await this.repo.upsertPresence({
      userId,
      availability: input.availability,
      lat: input.lat,
      lng: input.lng,
      accuracyM: input.accuracyM,
      now,
      ttlMs: this.PRESENCE_TTL_MS,
    });
    return { ok: true as const };
  }

  async clearPresence(userId: string) {
    await this.repo.deletePresence(userId);
    return { ok: true as const };
  }

  async getNearby(
    viewerId: string,
    query: z.infer<typeof nearbyQuerySchema>,
  ) {
    const now = this.clock.now();

    // BR-14: anchored to user's own last reported position
    const viewerPresence = await this.repo.getPresence(viewerId);
    if (
      !viewerPresence ||
      viewerPresence.availability === "OFF" ||
      viewerPresence.expiresAt <= now
    ) {
      throw new AppError("presence_required", 400);
    }

    // Fetch viewer profile
    const viewerProfile = await this.repo.getPublicProfile(viewerId);
    const viewerRadius = Math.min(
      query.radiusKm ?? viewerProfile?.profile?.discoveryRadiusKm ?? 5,
      25,
    );
    const viewerInterests = new Set(viewerProfile?.profile?.interests ?? []);

    // BR-11: Blocked pairs invisible
    const blockedIds = await this.safetyRepo.getBlockedUserIds(viewerId);

    // Eligible presences (BR-03, BR-11, BR-13, §11.5)
    const eligiblePresences = await this.repo.findEligibleActivePresences(
      viewerId,
      blockedIds,
      now,
    );

    const filterInterests = query.interests
      ? query.interests.split(",").map((s) => s.trim().toLowerCase())
      : null;

    interface MatchItem {
      userId: string;
      displayName: string;
      age: number | null;
      photoUrl: string | null;
      availability: "OPEN" | "BROWSING" | "AT_EVENT";
      distanceKm: number;
      distanceBucket: string;
      marker: { lat: number; lng: number };
      sharedInterests: string[];
      updatedAt: Date;
      upFor: string | null;
      upForUntil: string | null;
    }

    const matches: MatchItem[] = [];

    for (const p of eligiblePresences) {
      if (p.availability === "OFF") continue;

      const targetProfile = p.user.profile;
      if (!targetProfile) continue;

      const distanceKm = haversineDistanceKm(
        viewerPresence.lat,
        viewerPresence.lng,
        p.lat,
        p.lng,
      );

      // Must be within both viewer's radius and target's own radius (BR-12, §11.5)
      const targetRadius = Math.min(targetProfile.discoveryRadiusKm, 25);
      if (distanceKm > viewerRadius || distanceKm > targetRadius) {
        continue;
      }

      // Check interest filters if provided
      const targetInterests = targetProfile.interests ?? [];
      const sharedInterests = targetInterests.filter((i) =>
        viewerInterests.has(i),
      );

      if (filterInterests && filterInterests.length > 0) {
        const matchesFilter = targetInterests.some((i) =>
          filterInterests.includes(i.toLowerCase()),
        );
        if (!matchesFilter) continue;
      }

      // Age calculation if showAge is enabled
      let age: number | null = null;
      if (targetProfile.showAge && p.user.dateOfBirth) {
        const diffMs = now.getTime() - p.user.dateOfBirth.getTime();
        age = Math.floor(diffMs / (365.25 * 24 * 60 * 60 * 1000));
      }

      // BR-10: Exact coordinates NEVER returned. Stable HMAC fuzzing within grid cell (§11.3)
      const marker = computeFuzzedMarker(
        this.env.JWT_REFRESH_PEPPER,
        p.userId,
        p.lat,
        p.lng,
        now,
      );

      matches.push({
        userId: p.userId,
        displayName: targetProfile.displayName,
        age,
        photoUrl: p.user.photos[0]?.url ?? null,
        availability: p.availability as "OPEN" | "BROWSING" | "AT_EVENT",
        distanceKm,
        distanceBucket: toDistanceBucket(distanceKm),
        marker,
        sharedInterests,
        updatedAt: p.updatedAt,
        upFor: targetProfile.upForUntil && targetProfile.upForUntil > now ? targetProfile.upFor : null,
        upForUntil: targetProfile.upForUntil && targetProfile.upForUntil > now ? targetProfile.upForUntil.toISOString() : null,
      });
    }

    // §11.4: Sort by bucket, then recent activity, then stable order
    matches.sort((a, b) => {
      if (a.distanceBucket !== b.distanceBucket) {
        return a.distanceKm - b.distanceKm;
      }
      return b.updatedAt.getTime() - a.updatedAt.getTime();
    });

    const limit = query.limit ?? 20;
    let startIndex = 0;
    if (query.cursor) {
      const idx = matches.findIndex((m) => m.userId === query.cursor);
      if (idx !== -1) startIndex = idx + 1;
    }

    const paged = matches.slice(startIndex, startIndex + limit);
    const nextCursor =
      startIndex + limit < matches.length
        ? paged[paged.length - 1]?.userId ?? null
        : null;

    return {
      items: paged.map(({ distanceKm, updatedAt, ...rest }) => rest),
      nextCursor,
    };
  }

  async getUserProfile(viewerId: string, targetUserId: string) {
    // BR-11: Blocked pairs invisible
    const isBlocked = await this.safetyRepo.isBlockedPair(viewerId, targetUserId);
    if (isBlocked) {
      throw new AppError("not_found", 404);
    }

    const user = await this.repo.getPublicProfile(targetUserId);
    if (
      !user ||
      user.status !== "ACTIVE" ||
      !user.adultVerifiedAt ||
      !user.guidelinesAcceptedAt ||
      !user.profile
    ) {
      throw new AppError("not_found", 404);
    }

    let age: number | null = null;
    if (user.profile.showAge && user.dateOfBirth) {
      const diffMs = this.clock.now().getTime() - user.dateOfBirth.getTime();
      age = Math.floor(diffMs / (365.25 * 24 * 60 * 60 * 1000));
    }

    return {
      userId: user.id,
      displayName: user.profile.displayName,
      bio: user.profile.bio,
      prompts: Array.isArray(user.profile.prompts) ? user.profile.prompts as { id: string; answer: string }[] : [],
      idealWeekend: user.profile.idealWeekend,
      upFor: user.profile.upForUntil && user.profile.upForUntil > this.clock.now() ? user.profile.upFor : null,
      upForUntil: user.profile.upForUntil && user.profile.upForUntil > this.clock.now() ? user.profile.upForUntil.toISOString() : null,
      phoneVerified: !!user.phoneVerifiedAt,
      voiceBio: await this.repo.getVoiceBio(user.id),
      age,
      photos: user.photos.map((p) => ({
        id: p.id,
        url: p.url,
        position: p.position,
      })),
      interests: user.profile.interests,
      availability: user.presence && user.presence.expiresAt > this.clock.now() ? user.presence.availability : null,
    };
  }
}
