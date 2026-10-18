import { v7 as uuid } from "uuid";
import type { Database } from "../../lib/database.js";
import { SafetyRepository } from "../safety/safety.repo.js";

export function weekKey(now: Date): string {
  const monday = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  monday.setUTCDate(monday.getUTCDate() - (monday.getUTCDay() + 6) % 7);
  return monday.toISOString().slice(0, 10);
}

/** Idempotent in-app delivery; no unsolicited email or push subscription. */
export async function deliverWeeklyDigests(db: Database, now: Date): Promise<void> {
  const safety = new SafetyRepository(db);
  let after: string | undefined;
  for (;;) {
    const subscribers = await db.notificationPreference.findMany({
      where: { category: "weekly_digest", inApp: true, user: { status: "ACTIVE" }, ...(after ? { userId: { gt: after } } : {}) },
      include: { user: { include: { profile: true } } }, orderBy: { userId: "asc" }, take: 100,
    });
    if (subscribers.length === 0) break;
    for (const subscriber of subscribers) {
      const userId = subscriber.userId;
      const interests = subscriber.user.profile?.interests ?? [];
      const blocked = [...await safety.getBlockedUserIds(userId)];
      const saved = await db.savedExperience.findMany({ where: { userId, kind: "EVENT" }, select: { targetId: true }, take: 100 });
      const events = await db.event.findMany({ where: {
        status: "PUBLISHED", startsAt: { gt: now, lte: new Date(now.getTime() + 7 * 86400000) },
        organizerId: { notIn: blocked }, organizer: { status: "ACTIVE" },
        OR: [{ category: { in: interests } }, { id: { in: saved.map(s => s.targetId) } }],
      }, orderBy: { startsAt: "asc" }, take: 5 });
      if (events.length === 0) continue;
      // Recheck opt-in inside the delivery transaction, serialized with preference updates.
      await db.$transaction(async tx => {
        await tx.$queryRaw`SELECT "userId" FROM "NotificationPreference" WHERE "userId" = ${userId}::uuid AND category = 'weekly_digest' FOR UPDATE`;
        const pref = await tx.notificationPreference.findUnique({ where: { userId_category: { userId, category: "weekly_digest" } } });
        if (!pref?.inApp) return;
        const dedupeKey = `weekly:${weekKey(now)}`;
        await tx.notification.upsert({ where: { userId_dedupeKey: { userId, dedupeKey } }, create: {
          id: uuid(), userId, type: "WEEKLY_DIGEST", dedupeKey,
          payload: { week: weekKey(now), eventIds: events.map(e => e.id), count: events.length },
        }, update: {} });
      });
    }
    after = subscribers[subscribers.length - 1]?.userId;
    if (subscribers.length < 100) break;
  }
}
