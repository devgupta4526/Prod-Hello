import { memoryImage } from "./memory-image.js";
import { v7 as uuid } from "uuid";
import type { FastifyInstance, FastifyRequest } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import { z } from "zod";
import type { Database } from "../../lib/database.js";
import type { Clock } from "../../lib/clock.js";
import { AppError } from "../../lib/app-error.js";
import { ChatRepository } from "../chat/chat.repo.js";
import { SafetyRepository } from "../safety/safety.repo.js";
import type { DiscoveryService } from "../discovery/discovery.service.js";
import { personalitySchema, upForSchema, planInputSchema } from "./social.schemas.js";

export function socialRoutes(app: FastifyInstance, db: Database, clock: Clock, discovery: DiscoveryService) {
  const r = app.withTypeProvider<ZodTypeProvider>();
  const config = { access: "session" as const, returnsPeople: true };
  const chat = new ChatRepository(db);
  const safety = new SafetyRepository(db);
  const idParams = z.object({ id: z.uuid() });
  function viewer(req: FastifyRequest) {
    const u = req.identity?.user;
    if (!u || u.status !== "ACTIVE" || !u.adultVerifiedAt || !u.guidelinesAcceptedAt) throw new AppError("forbidden", 403);
    return u.id;
  }
  function icebreakerDay() {
    return new Date(clock.now().getTime() + 19800000).toISOString().slice(0, 10);
  }
  r.get("/v1/discovery/icebreaker", { config }, async req => {
    const userId = viewer(req);
    const day = icebreakerDay();
    const mine = await db.icebreakerAnswer.findUnique({ where: { day_userId: { day, userId } } });
    const nearby = (await discovery.getNearby(userId, { limit: 50 })).items;
    const answers = await db.icebreakerAnswer.findMany({ where: { day, userId: { in: nearby.map(person => person.userId) } }, take: 50 });
    return { day, question: "What’s the best late-night food spot in the city?", expiresAt: new Date(Date.parse(`${day}T00:00:00+05:30`) + 86400000).toISOString(), answer: mine?.answer ?? null, totalAnswers: answers.length, items: mine ? answers.flatMap(answer => {
      const person = nearby.find(person => person.userId === answer.userId);
      return person ? [{ userId: answer.userId, displayName: person.displayName, distanceBucket: person.distanceBucket, answer: answer.answer }] : [];
    }) : [] };
  });
  r.put("/v1/discovery/icebreaker", { config, schema: { body: z.object({ day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), answer: z.string().trim().min(1).max(280) }).strict() } }, async req => {
    const userId = viewer(req);
    if (req.body.day !== icebreakerDay()) throw new AppError("context_unavailable", 409);
    await db.icebreakerAnswer.upsert({ where: { day_userId: { day: req.body.day, userId } }, create: { day: req.body.day, userId, answer: req.body.answer }, update: { answer: req.body.answer } });
    return { ok: true };
  });
  async function members(conversationId: string, userId: string) {
    const c = await chat.getConversationForUser(conversationId, userId);
    const ids = c.connection ? [c.connection.userLowId, c.connection.userHighId] : (c.pool?.members.map(m => m.userId) ?? []);
    const eligible = await db.user.findMany({ where: { id: { in: ids }, status: "ACTIVE", adultVerifiedAt: { not: null }, guidelinesAcceptedAt: { not: null } }, select: { id: true } });
    return eligible.map(u => u.id);
  }
  async function planAccess(id: string, userId: string) {
    const p = await db.socialPlan.findUnique({ where: { id }, include: { votes: true } });
    if (!p) throw new AppError("not_found", 404);
    if (!p.participantIds.includes(userId)) throw new AppError("forbidden", 403);
    const participants = (await members(p.conversationId, userId)).filter(id => p.participantIds.includes(id));
    const blocked = await safety.getBlockedUserIds(userId);
    if (participants.some(id => blocked.has(id))) throw new AppError("not_found", 404);
    return { p, participants };
  }
  async function circleAccess(id: string, userId: string) {
    const c = await db.interestCircle.findUnique({ where: { id }, include: { members: true } });
    if (!c || await safety.isBlockedPair(userId, c.hostId)) throw new AppError("not_found", 404);
    const host = await db.user.findUnique({ where: { id: c.hostId }, select: { status: true } });
    if (host?.status !== "ACTIVE") throw new AppError("not_found", 404);
    return c;
  }
  r.get("/v1/me/personality", { config }, async req => {
    const userId = viewer(req);
    const profile = await db.profile.findUniqueOrThrow({ where: { userId } });
    return { prompts: profile.prompts, idealWeekend: profile.idealWeekend, upFor: profile.upForUntil && profile.upForUntil > clock.now() ? profile.upFor : null, upForUntil: profile.upForUntil };
  });
  r.put("/v1/me/personality", { config, schema: { body: personalitySchema } }, async req => {
    await db.profile.update({ where: { userId: viewer(req) }, data: req.body });
    return { ok: true };
  });
  r.put("/v1/me/up-for", { config, schema: { body: upForSchema } }, async req => {
    await db.profile.update({ where: { userId: viewer(req) }, data: { upFor: req.body.activity, upForUntil: req.body.activity ? new Date(clock.now().getTime() + req.body.hours * 3600000) : null } });
    return { ok: true };
  });
  r.get("/v1/conversations/:id/plans", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); await members(req.params.id, userId);
    const blocked = [...await safety.getBlockedUserIds(userId)];
    return { items: await db.socialPlan.findMany({ where: { conversationId: req.params.id, participantIds: { has: userId }, NOT: { participantIds: { hasSome: blocked } } }, include: { votes: true }, orderBy: { createdAt: "desc" }, take: 50 }), userId };
  });
  r.post("/v1/conversations/:id/plans", { config, schema: { params: idParams, body: planInputSchema } }, async req => {
    const userId = viewer(req); const participantIds = await members(req.params.id, userId);
    const blocked = await safety.getBlockedUserIds(userId);
    if (participantIds.some(id => blocked.has(id))) throw new AppError("not_found", 404);
    const times = req.body.timeOptions.map(t => new Date(t));
    if (times.some(t => t <= clock.now() || t.getTime() > clock.now().getTime() + 90 * 86400000)) throw new AppError("invalid_plan_time", 400);
    return db.$transaction(async tx => {
      const plan = await tx.socialPlan.create({ data: { id: uuid(), participantIds, conversationId: req.params.id, creatorId: userId, activity: req.body.activity, durationMinutes: req.body.durationMinutes, venue: req.body.venue, timeOptions: times, votes: { create: { userId, option: 0 } } } });
      for (const recipient of participantIds.filter(id => id !== userId)) await tx.notification.create({ data: { id: uuid(), userId: recipient, type: "PLAN_PROPOSED", dedupeKey: `plan-proposed:${plan.id}`, payload: { planId: plan.id, conversationId: plan.conversationId, title: req.body.activity, message: req.body.venue } } });
      return plan;
    });
  });
  r.post("/v1/plans/:id/respond", { config, schema: { params: idParams, body: z.object({ response: z.enum(["ACCEPT", "DECLINE"]), option: z.number().int().min(0).max(2).default(0), expectedTime: z.iso.datetime(), note: z.string().trim().max(240).default("") }).strict() } }, async req => {
    const userId = viewer(req); const { p, participants } = await planAccess(req.params.id, userId);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id }, include: { votes: true } });
      const time = current.timeOptions[req.body.option];
      if (!time || time.toISOString() !== req.body.expectedTime || time <= clock.now()) throw new AppError("plan_closed", 409);
      if (current.status === "CONFIRMED" && req.body.response === "ACCEPT") return { status: "CONFIRMED" };
      if (current.status !== "PROPOSED") throw new AppError("plan_closed", 409);
      if (req.body.response === "DECLINE") {
        await tx.socialPlan.update({ where: { id: p.id }, data: { status: "DECLINED", confirmedTime: null } });
      } else {
        await tx.planVote.upsert({ where: { planId_userId: { planId: p.id, userId } }, create: { planId: p.id, userId, option: req.body.option }, update: { option: req.body.option } });
      }
      const allAccepted = req.body.response === "ACCEPT" && participants.length >= 2 && participants.every(id => id === userId || current.votes.some(v => v.userId === id && v.option === req.body.option));
      const status = req.body.response === "DECLINE" ? "DECLINED" : allAccepted ? "CONFIRMED" : "PROPOSED";
      if (allAccepted) await tx.socialPlan.update({ where: { id: p.id }, data: { status, confirmedTime: time, participantIds: participants } });
      if (status !== "PROPOSED") for (const recipient of participants) await tx.notification.upsert({
        where: { userId_dedupeKey: { userId: recipient, dedupeKey: `plan-${status.toLowerCase()}:${p.id}:${time.toISOString()}` } },
        create: { id: uuid(), userId: recipient, type: `PLAN_${status}`, dedupeKey: `plan-${status.toLowerCase()}:${p.id}:${time.toISOString()}`, payload: { planId: p.id, conversationId: p.conversationId, title: status === "CONFIRMED" ? "It’s a plan." : "Invitation declined.", message: req.body.note || current.activity } }, update: {},
      });
      return { status };
    });
  });
  r.post("/v1/plans/:id/reschedule", { config, schema: { params: idParams, body: z.object({ time: z.iso.datetime(), expectedTime: z.iso.datetime(), note: z.string().trim().max(240).default("") }).strict() } }, async req => {
    const userId = viewer(req); const { p, participants } = await planAccess(req.params.id, userId);
    const time = new Date(req.body.time);
    if (time <= clock.now() || time.getTime() > clock.now().getTime() + 90 * 86400000) throw new AppError("invalid_plan_time", 400);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id } });
      if (!["PROPOSED", "CONFIRMED"].includes(current.status) || (current.confirmedTime ?? current.timeOptions[0])?.toISOString() !== req.body.expectedTime) throw new AppError("plan_closed", 409);
      await tx.planVote.deleteMany({ where: { planId: p.id } });
      await tx.socialPlan.update({ where: { id: p.id }, data: { timeOptions: [time], status: "PROPOSED", confirmedTime: null, votes: { create: { userId, option: 0 } } } });
      for (const recipient of participants.filter(id => id !== userId)) await tx.notification.create({ data: { id: uuid(), userId: recipient, type: "PLAN_RESCHEDULED", dedupeKey: `plan-rescheduled:${uuid()}`, payload: { planId: p.id, conversationId: p.conversationId, title: "New time proposed.", message: req.body.note || time.toISOString() } } });
      return { status: "PROPOSED" };
    });
  });
  r.put("/v1/plans/:id/vote", { config, schema: { params: idParams, body: z.object({ option: z.number().int().min(0).max(2) }).strict() } }, async req => {
    const userId = viewer(req); const { p } = await planAccess(req.params.id, userId);
    const selectedTime = p.timeOptions[req.body.option];
    if (!selectedTime || selectedTime <= clock.now()) throw new AppError("invalid_plan_time", 400);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id } });
      if (current.status !== "PROPOSED") throw new AppError("plan_closed", 409);
      await tx.planVote.upsert({ where: { planId_userId: { planId: p.id, userId } }, create: { planId: p.id, userId, option: req.body.option }, update: { option: req.body.option } });
      return { ok: true };
    });
  });
  r.post("/v1/plans/:id/confirm", { config, schema: { params: idParams, body: z.object({ option: z.number().int().min(0).max(2) }).strict() } }, async req => {
    const userId = viewer(req); const { p, participants } = await planAccess(req.params.id, userId);
    if (p.creatorId !== userId) throw new AppError("forbidden", 403);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id }, include: { votes: true } });
      const time = current.timeOptions[req.body.option];
      if (current.status !== "PROPOSED" || !time || time <= clock.now()) throw new AppError("plan_closed", 409);
      if (participants.length < 2 || !participants.every(id => current.votes.some(v => v.userId === id && v.option === req.body.option))) throw new AppError("agreement_required", 409);
      await tx.socialPlan.update({ where: { id: p.id }, data: { status: "CONFIRMED", confirmedTime: time, participantIds: participants } });
      for (const recipient of participants) await tx.notification.upsert({
        where: { userId_dedupeKey: { userId: recipient, dedupeKey: `plan-confirmed:${p.id}` } },
        create: { id: uuid(), userId: recipient, type: "PLAN_CONFIRMED", dedupeKey: `plan-confirmed:${p.id}`, payload: { planId: p.id, conversationId: p.conversationId, activity: p.activity, startsAt: time.toISOString() } }, update: {},
      });
      return { ok: true };
    });
  });
  r.post("/v1/plans/:id/cancel", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); const { p } = await planAccess(req.params.id, userId);
    await db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
        const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id } });
        if (current.status === "CANCELLED") return;
        if (!["PROPOSED", "CONFIRMED"].includes(current.status)) throw new AppError("plan_closed", 409);
      await tx.socialPlan.update({ where: { id: p.id }, data: { status: "CANCELLED" } });
      for (const recipient of p.participantIds) await tx.notification.upsert({
        where: { userId_dedupeKey: { userId: recipient, dedupeKey: `plan-cancelled:${p.id}` } },
        create: { id: uuid(), userId: recipient, type: "PLAN_CANCELLED", dedupeKey: `plan-cancelled:${p.id}`, payload: { planId: p.id, conversationId: p.conversationId, activity: p.activity } }, update: {},
      });
    });
    return { ok: true };
  });
  r.get("/v1/circles", { config }, async req => {
    const userId = viewer(req); const blocked = [...await safety.getBlockedUserIds(userId)];
    const inactiveHosts = await db.user.findMany({ where: { status: { not: "ACTIVE" } }, select: { id: true } });
    blocked.push(...inactiveHosts.map(u => u.id));
    const circles = await db.interestCircle.findMany({ where: { hostId: { notIn: blocked }, NOT: { members: { some: { userId, removed: true } } } }, include: { members: true }, take: 50, orderBy: { createdAt: "desc" } });
    return { items: circles.map(c => ({ id: c.id, name: c.name, interest: c.interest, rules: c.rules, capacity: c.capacity, count: c.members.filter(m => !m.removed).length, joined: c.members.some(m => m.userId === userId && !m.removed), isHost: c.hostId === userId })) };
  });
  r.post("/v1/circles", { config, schema: { body: z.object({ name: z.string().trim().min(3).max(80), interest: z.string().trim().min(2).max(40), rules: z.string().trim().min(10).max(1000), capacity: z.number().int().min(2).max(50).default(30) }).strict() } }, async req => {
    const userId = viewer(req);
    return db.interestCircle.create({ data: { ...req.body, id: uuid(), hostId: userId, members: { create: { userId } } } });
  });
  r.put("/v1/circles/:id/membership", { config, schema: { params: idParams, body: z.object({ joined: z.boolean(), acceptRules: z.boolean() }).strict() } }, async req => {
    const userId = viewer(req); const c = await circleAccess(req.params.id, userId);
    if (c.hostId === userId) throw new AppError("host_cannot_leave", 409);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "InterestCircle" WHERE id = ${c.id}::uuid FOR UPDATE`;
      const existing = await tx.circleMember.findUnique({ where: { circleId_userId: { circleId: c.id, userId } } });
      if (existing?.removed) throw new AppError("forbidden", 403);
      if (!req.body.joined) { await tx.circleMember.deleteMany({ where: { circleId: c.id, userId } }); return { ok: true }; }
      if (!req.body.acceptRules) throw new AppError("rules_required", 400);
      if (!existing && await tx.circleMember.count({ where: { circleId: c.id, removed: false } }) >= c.capacity) throw new AppError("circle_full", 409);
      await tx.circleMember.upsert({ where: { circleId_userId: { circleId: c.id, userId } }, create: { circleId: c.id, userId }, update: {} });
      return { ok: true };
    });
  });
  r.get("/v1/circles/:id/members", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); const c = await circleAccess(req.params.id, userId);
    if (!c.members.some(m => m.userId === userId && !m.removed)) throw new AppError("forbidden", 403);
    const blocked = await safety.getBlockedUserIds(userId);
    const users = await db.user.findMany({ where: { id: { in: c.members.filter(m => !m.removed && !blocked.has(m.userId)).map(m => m.userId) }, status: "ACTIVE" }, include: { profile: true } });
    return { items: users.map(u => ({ id: u.id, displayName: u.profile?.displayName ?? "Member", phoneVerified: !!u.phoneVerifiedAt })) };
  });
  r.delete("/v1/circles/:id/members/:userId", { config, schema: { params: z.object({ id: z.uuid(), userId: z.uuid() }) } }, async req => {
    const userId = viewer(req); const c = await circleAccess(req.params.id, userId);
    if (c.hostId !== userId || req.params.userId === userId) throw new AppError("forbidden", 403);
    await db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "InterestCircle" WHERE id = ${c.id}::uuid FOR UPDATE`;
      await tx.circleMember.updateMany({ where: { circleId: c.id, userId: req.params.userId }, data: { removed: true } });
    });
    return { ok: true };
  });
  async function circleMember(id: string, userId: string) {
    const c = await circleAccess(id, userId);
    if (!c.members.some(m => m.userId === userId && !m.removed)) throw new AppError("forbidden", 403);
    return c;
  }
  r.get("/v1/circles/:id/posts", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); const c = await circleMember(req.params.id, userId);
    const blocked = [...await safety.getBlockedUserIds(userId)];
    const posts = await db.circlePost.findMany({ where: { circleId: c.id, authorId: { notIn: blocked }, author: { status: "ACTIVE" } }, include: { author: { include: { profile: true } } }, orderBy: { createdAt: "desc" }, take: 50 });
    return { items: posts.map(p => ({ id: p.id, body: p.body, authorId: p.authorId, displayName: p.author.profile?.displayName ?? "Member", createdAt: p.createdAt, canRemove: p.authorId === userId || c.hostId === userId })) };
  });
  r.post("/v1/circles/:id/posts", { config, schema: { params: idParams, body: z.object({ body: z.string().trim().min(1).max(1000) }).strict() } }, async req => {
    const userId = viewer(req); await circleMember(req.params.id, userId);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "InterestCircle" WHERE id = ${req.params.id}::uuid FOR UPDATE`;
      const membership = await tx.circleMember.findUnique({ where: { circleId_userId: { circleId: req.params.id, userId } } });
      if (!membership || membership.removed) throw new AppError("forbidden", 403);
      return tx.circlePost.create({ data: { id: uuid(), circleId: req.params.id, authorId: userId, body: req.body.body } });
    });
  });
  r.delete("/v1/circles/:id/posts/:postId", { config, schema: { params: z.object({ id: z.uuid(), postId: z.uuid() }) } }, async req => {
    const userId = viewer(req); const c = await circleMember(req.params.id, userId);
    const post = await db.circlePost.findUnique({ where: { id: req.params.postId } });
    if (!post || post.circleId !== c.id) throw new AppError("not_found", 404);
    if (post.authorId !== userId && c.hostId !== userId) throw new AppError("forbidden", 403);
    await db.circlePost.delete({ where: { id: post.id } }); return { ok: true };
  });
  const savedBody = z.object({ kind: z.enum(["EVENT", "POOL"]), targetId: z.uuid(), saved: z.boolean() }).strict();
  r.put("/v1/me/saved", { config, schema: { body: savedBody } }, async req => {
    const userId = viewer(req); const { kind, targetId, saved } = req.body;
    if (saved) {
      const target = kind === "EVENT" ? await db.event.findUnique({ where: { id: targetId } }) : await db.pool.findUnique({ where: { id: targetId } });
      if (!target || !["PUBLISHED", "OPEN", "FULL"].includes(target.status) || await safety.isBlockedPair(userId, target.organizerId)) throw new AppError("not_found", 404);
      await db.savedExperience.upsert({ where: { userId_targetId_kind: { userId, targetId, kind } }, create: { userId, targetId, kind }, update: {} });
    } else await db.savedExperience.deleteMany({ where: { userId, targetId, kind } });
    return { ok: true };
  });
  r.put("/v1/me/weekly-digest", { config, schema: { body: z.object({ enabled: z.boolean() }).strict() } }, async req => {
    const userId = viewer(req);
    await db.notificationPreference.upsert({ where: { userId_category: { userId, category: "weekly_digest" } }, create: { userId, category: "weekly_digest", push: false, inApp: req.body.enabled }, update: { inApp: req.body.enabled } });
    return { ok: true };
  });
  r.get("/v1/home", { config }, async req => {
    const userId = viewer(req); const blocked = [...await safety.getBlockedUserIds(userId)];
    const profile = await db.profile.findUniqueOrThrow({ where: { userId } });
    const saved = await db.savedExperience.findMany({ where: { userId }, orderBy: { createdAt: "desc" }, take: 100 });
    const events = await db.event.findMany({ where: { status: "PUBLISHED", startsAt: { gt: clock.now() }, organizerId: { notIn: blocked } }, orderBy: { startsAt: "asc" }, take: 100 });
    const presence = await db.presence.findUnique({ where: { userId } });
    const pools = presence && presence.expiresAt > clock.now() ? await db.pool.findMany({ where: { status: { in: ["OPEN", "FULL"] }, startsAt: { gt: clock.now() }, organizerId: { notIn: blocked }, areaCell: presence.cellId }, orderBy: { startsAt: "asc" }, take: 30 }) : [];
    const conversations = await chat.listConversationsForUser(userId, new Set(blocked));
    const plans = await db.socialPlan.findMany({ where: { conversationId: { in: conversations.map(c => c.id) }, participantIds: { has: userId }, NOT: { participantIds: { hasSome: blocked } }, status: { not: "CANCELLED" } }, orderBy: { createdAt: "desc" }, take: 30 });
    const digest = await db.notificationPreference.findUnique({ where: { userId_category: { userId, category: "weekly_digest" } } });
    const cards = [...events.map(e => ({ id: e.id, kind: "EVENT", title: e.title, startsAt: e.startsAt, category: e.category })), ...pools.map(p => ({ id: p.id, kind: "POOL", title: p.title, startsAt: p.startsAt, category: p.category }))];
    const savedEvents = await db.event.findMany({ where: { id: { in: saved.filter(s => s.kind === "EVENT").map(s => s.targetId) }, status: "PUBLISHED", organizerId: { notIn: blocked } } });
    const savedPools = await db.pool.findMany({ where: { id: { in: saved.filter(s => s.kind === "POOL").map(s => s.targetId) }, status: { in: ["OPEN", "FULL", "CLOSED"] }, organizerId: { notIn: blocked } } });
    const relevantPeople = presence && presence.expiresAt > clock.now() && presence.availability !== "OFF"
      ? (await discovery.getNearby(userId, { limit: 50 })).items.sort((a, b) => b.sharedInterests.length - a.sharedInterests.length).slice(0, 6)
      : [];
    const commitments = await db.ticket.findMany({ where: { ownerId: userId, status: "CONFIRMED", event: { status: "PUBLISHED", startsAt: { gt: clock.now() } } }, include: { event: true }, orderBy: { createdAt: "desc" }, take: 30 });
    return { relevantPeople, commitments: commitments.map(t => ({ id: t.id, title: t.event.title, startsAt: t.event.startsAt })), plans, nearbyPlans: pools.map(p => ({ id: p.id, title: p.title, startsAt: p.startsAt })), experiences: cards.sort((a,b) => Number(profile.interests.includes(b.category)) - Number(profile.interests.includes(a.category))).slice(0,20), saved: [...savedEvents.map(e => ({ id: e.id, kind: "EVENT", title: e.title, startsAt: e.startsAt })), ...savedPools.map(p => ({ id: p.id, kind: "POOL", title: p.title, startsAt: p.startsAt }))], weeklyDigest: digest?.inApp ?? false, shortlist: digest?.inApp ? cards.filter(c => c.startsAt.getTime() <= clock.now().getTime() + 7 * 86400000 && profile.interests.includes(c.category)).slice(0,5) : [] };
  });
  r.get("/v1/plans/:id/participants", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); const { participants } = await planAccess(req.params.id, userId);
    const blocked = await safety.getBlockedUserIds(userId);
    const users = await db.user.findMany({ where: { id: { in: participants.filter(id => id !== userId && !blocked.has(id)) }, status: "ACTIVE" }, include: { profile: true } });
    return { items: users.map(u => ({ id: u.id, displayName: u.profile?.displayName ?? "Member" })) };
  });
  r.put("/v1/plans/:id/reconnect", { config, schema: { params: idParams, body: z.object({ targetId: z.uuid(), activity: z.string().trim().min(2).max(100), interested: z.boolean() }).strict() } }, async req => {
    const userId = viewer(req); const { p, participants } = await planAccess(req.params.id, userId);
    const { targetId, activity, interested } = req.body;
    if (p.status !== "CONFIRMED" || !p.confirmedTime || p.confirmedTime.getTime() + p.durationMinutes * 60000 > clock.now().getTime()) throw new AppError("plan_not_finished", 409);
    if (targetId === userId || !participants.includes(targetId) || await safety.isBlockedPair(userId, targetId)) throw new AppError("not_found", 404);
    return db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id } });
      if (interested && current.status !== "CONFIRMED") throw new AppError("plan_closed", 409);
    if (!interested) {
      await tx.reconnectChoice.deleteMany({ where: { planId: p.id, userId, targetId } });
      await tx.notification.deleteMany({ where: { dedupeKey: `reconnect:${p.id}:${[userId,targetId].sort().join(":")}` } });
      return { mutual: false };
    }
    await tx.reconnectChoice.upsert({ where: { planId_userId_targetId: { planId: p.id, userId, targetId } }, create: { planId: p.id, userId, targetId, activity }, update: { activity } });
    const other = await tx.reconnectChoice.findUnique({ where: { planId_userId_targetId: { planId: p.id, userId: targetId, targetId: userId } } });
    if (other) {
      for (const recipient of [userId, targetId]) await tx.notification.upsert({ where: { userId_dedupeKey: { userId: recipient, dedupeKey: `reconnect:${p.id}:${[userId,targetId].sort().join(":")}` } }, create: { id: uuid(), userId: recipient, type: "MEET_AGAIN", dedupeKey: `reconnect:${p.id}:${[userId,targetId].sort().join(":")}`, payload: { planId: p.id, conversationId: p.conversationId, partnerId: recipient === userId ? targetId : userId, activity, otherActivity: other.activity } }, update: {} });
    }
    return { mutual: !!other, activity: other ? other.activity : null };
    });
  });
  r.get("/v1/plans/:id/memories", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); await planAccess(req.params.id, userId);
    const blocked = [...await safety.getBlockedUserIds(userId)];
    return { items: await db.planMemory.findMany({ where: { planId: req.params.id, owner: { status: "ACTIVE" }, ownerId: { notIn: blocked }, OR: [{ ownerId: userId }, { audience: "GROUP" }] }, select: { id: true, ownerId: true, caption: true, audience: true, createdAt: true }, take: 50, orderBy: { createdAt: "desc" } }), userId };
  });
  r.get("/v1/memories/:id/image", { config, schema: { params: idParams } }, async (req, reply) => {
    const userId = viewer(req); const m = await db.planMemory.findUnique({ where: { id: req.params.id } });
    if (!m || (m.ownerId !== userId && m.audience !== "GROUP") || await safety.isBlockedPair(userId, m.ownerId)) throw new AppError("not_found", 404);
    const owner = await db.user.findUnique({ where: { id: m.ownerId }, select: { status: true } });
    if (owner?.status !== "ACTIVE") throw new AppError("not_found", 404);
    await planAccess(m.planId, userId);
    return reply.header("Cache-Control", "private, no-store").type("image/jpeg").send(Buffer.from(m.image));
  });
  r.post("/v1/plans/:id/memories", { config, schema: { params: idParams, body: z.object({ caption: z.string().trim().max(240), imageBase64: z.string().min(4).max(95000).regex(/^[A-Za-z0-9+/]+={0,2}$/), audience: z.enum(["PRIVATE", "GROUP"]), consent: z.literal(true) }).strict() } }, async req => {
    const userId = viewer(req); const { p } = await planAccess(req.params.id, userId);
    if (p.status !== "CONFIRMED" || !p.confirmedTime || p.confirmedTime > clock.now()) throw new AppError("plan_not_started", 409);
    const image = memoryImage(req.body.imageBase64);
    const m = await db.$transaction(async tx => {
      await tx.$queryRaw`SELECT id FROM "SocialPlan" WHERE id = ${p.id}::uuid FOR UPDATE`;
      const current = await tx.socialPlan.findUniqueOrThrow({ where: { id: p.id } });
      if (current.status !== "CONFIRMED") throw new AppError("plan_closed", 409);
      if (await tx.planMemory.count({ where: { planId: p.id, ownerId: userId } }) >= 20) throw new AppError("memory_limit", 409);
      return tx.planMemory.create({ data: { id: uuid(), planId: p.id, ownerId: userId, caption: req.body.caption, audience: req.body.audience, image: new Uint8Array(image) } });
    });
    return { id: m.id };
  });
  r.put("/v1/memories/:id/audience", { config, schema: { params: idParams, body: z.object({ audience: z.enum(["PRIVATE", "GROUP"]), consent: z.literal(true) }).strict() } }, async req => {
    const userId = viewer(req); const m = await db.planMemory.findUnique({ where: { id: req.params.id } });
    if (!m || m.ownerId !== userId) throw new AppError("not_found", 404);
    await planAccess(m.planId, userId);
    await db.planMemory.update({ where: { id: m.id }, data: { audience: req.body.audience, consentAt: clock.now() } }); return { ok: true };
  });
  r.delete("/v1/memories/:id", { config, schema: { params: idParams } }, async req => {
    const userId = viewer(req); const m = await db.planMemory.findUnique({ where: { id: req.params.id } });
    if (!m) throw new AppError("not_found", 404);
    if (m.ownerId !== userId) { const { p } = await planAccess(m.planId, userId); if (p.creatorId !== userId) throw new AppError("forbidden", 403); }
    await db.planMemory.delete({ where: { id: m.id } }); return { ok: true };
  });
}
