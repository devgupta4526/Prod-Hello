import { execFileSync } from "node:child_process";
import { PostgreSqlContainer } from "@testcontainers/postgresql";
import { beforeAll, afterAll, beforeEach, describe, it, expect } from "vitest";
import Fastify, { type FastifyInstance } from "fastify";
import { validatorCompiler, serializerCompiler } from "fastify-type-provider-zod";
import { v7 as uuid } from "uuid";
import { createDatabase, type Database } from "../src/lib/database.js";
import { AppError } from "../src/lib/app-error.js";
import type { Identity } from "../src/modules/auth/auth.service.js";
import { socialRoutes } from "../src/modules/social/social.routes.js";
import { DiscoveryService } from "../src/modules/discovery/discovery.service.js";
import { DiscoveryRepository } from "../src/modules/discovery/discovery.repo.js";
import { SafetyRepository } from "../src/modules/safety/safety.repo.js";
import { HelloRepository } from "../src/modules/hello/hello.repo.js";
import { HelloService } from "../src/modules/hello/hello.service.js";
import { deliverWeeklyDigests } from "../src/modules/social/weekly-digest.js";
import { testEnvironment } from "./environment.js";

let db: Database;
let app: FastifyInstance;
let discovery: DiscoveryService;
let hellos: HelloService;
let now = new Date("2026-10-18T10:00:00Z");
let alice: string, bob: string, outsider: string, conversation: string;
const cleanup: (() => Promise<unknown>)[] = [];
const clock = { now: () => now };
beforeAll(async () => {
  const container = await new PostgreSqlContainer("postgis/postgis:17-3.5").start();
  cleanup.unshift(() => container.stop());
  const url = container.getConnectionUri();
  execFileSync(process.execPath, ["node_modules/prisma/build/index.js", "migrate", "deploy"], { env: { ...process.env, DIRECT_URL: url }, stdio: "pipe" });
  db = createDatabase(url); cleanup.unshift(() => db.$disconnect());
  const safety = new SafetyRepository(db);
  const discoveryRepo = new DiscoveryRepository(db);
  discovery = new DiscoveryService(discoveryRepo, safety, testEnvironment(url), clock);
  hellos = new HelloService(new HelloRepository(db), safety, discoveryRepo, clock);
  app = Fastify();
  app.setValidatorCompiler(validatorCompiler); app.setSerializerCompiler(serializerCompiler);
  // Test-only identity fixture. Production routes use AuthService and signed sessions.
  app.decorateRequest("identity", null);
  app.addHook("preHandler", async request => {
    const id = request.headers["x-test-user"];
    const user = typeof id === "string" ? await db.user.findUnique({ where: { id } }) : null;
    request.identity = user ? { user } as unknown as Identity : null;
  });
  app.setErrorHandler((error, _req, reply) => reply.code(error instanceof AppError ? error.httpStatus : 400).send({ code: error instanceof AppError ? error.code : "invalid" }));
  socialRoutes(app, db, clock, discovery);
  await app.ready(); cleanup.unshift(() => app.close());
}, 180000);
afterAll(async () => { for (const close of cleanup) await close(); });
beforeEach(async () => {
  now = new Date("2026-10-18T10:00:00Z");
  [alice, bob, outsider] = [uuid(), uuid(), uuid()] as const;
  for (const [index,id] of [alice,bob,outsider].entries()) await db.user.create({ data: { id, phoneE164: `+91${id.replaceAll("-", "")}`, status: "ACTIVE", adultVerifiedAt: now, guidelinesAcceptedAt: now, phoneVerifiedAt: now, profile: { create: { displayName: `Member ${String(index)}`, interests: ["Coffee","Books","Music"] } } } });
  const pair = [alice,bob].sort();
  const connection = await db.connection.create({ data: { id: uuid(), userLowId: pair[0] as string, userHighId: pair[1] as string } });
  conversation = uuid();
  await db.conversation.create({ data: { id: conversation, connectionId: connection.id, type: "DM" } });
});
async function call(user: string, method: "GET" | "POST" | "PUT" | "DELETE", url: string, payload?: object) {
  return app.inject({ method, url, headers: { "x-test-user": user }, ...(payload ? { payload } : {}) });
}
async function propose() {
  const response = await call(alice,"POST",`/v1/conversations/${conversation}/plans`,{ activity: "Coffee", venue: "Public cafe on Main Street", publicVenue: true, timeOptions: ["2026-10-19T10:00:00Z","2026-10-19T12:00:00Z"] });
  expect(response.statusCode).toBe(200);
  return response.json<{ id: string }>().id;
}
async function confirm(id: string) {
  for(const user of [alice,bob]) expect((await call(user,"PUT",`/v1/plans/${id}/vote`,{ option: 0 })).statusCode).toBe(200);
  expect((await call(alice,"POST",`/v1/plans/${id}/confirm`,{ option: 0 })).statusCode).toBe(200);
}
describe("persistent social journeys", () => {
  it("requires current membership and unanimous agreement", async () => {
    const id = await propose();
    expect((await call(outsider,"GET",`/v1/conversations/${conversation}/plans`)).statusCode).toBe(403);
    expect((await call(alice,"POST",`/v1/plans/${id}/confirm`,{ option: 0 })).statusCode).toBe(409);
    expect((await call(bob,"POST",`/v1/plans/${id}/confirm`,{ option: 0 })).statusCode).toBe(403);
    await confirm(id);
    expect((await call(bob,"PUT",`/v1/plans/${id}/vote`,{ option: 1 })).statusCode).toBe(409);
    expect((await call(bob,"POST",`/v1/plans/${id}/cancel`)).statusCode).toBe(403);
  });
  it("hides plans after a block", async () => {
    const id = await propose();
    await db.block.create({ data: { id: uuid(), blockerId: alice, blockedId: bob } });
    expect((await call(bob,"GET",`/v1/conversations/${conversation}/plans`)).statusCode).toBe(404);
    expect((await call(bob,"PUT",`/v1/plans/${id}/vote`,{ option: 0 })).statusCode).toBe(404);
  });
  it("expires activity availability on reads", async () => {
    await call(alice,"PUT","/v1/me/up-for",{ activity: "Badminton", hours: 1 });
    expect((await discovery.getUserProfile(bob,alice)).upFor).toBe("Badminton");
    now = new Date(now.getTime() + 3600001);
    expect((await discovery.getUserProfile(bob,alice)).upFor).toBeNull();
  });
  it("validates contextual replies against the recipient's actual profile", async () => {
    const prompts = [{ id: "joy", answer: "Walking" },{ id: "learn", answer: "Guitar" },{ id: "friendship", answer: "Kindness" }];
    await call(bob,"PUT","/v1/me/personality",{ prompts, idealWeekend: "Books and coffee" });
    await new DiscoveryRepository(db).upsertPresence({ userId: bob, availability: "OPEN", lat: 0, lng: 0, now, ttlMs: 3600000 });
    await expect(hellos.sendHello(alice,{ receiverId: bob, message: "Hello", context: { type: "PHOTO", id: uuid() } })).rejects.toMatchObject({ code: "context_unavailable" });
    const sent = await hellos.sendHello(alice,{ receiverId: bob, message: "Let's walk", context: { type: "PROMPT", id: "joy", text: "Forged text" } });
    expect((await db.hello.findUniqueOrThrow({ where: { id: sent.hello.id } })).context).toMatchObject({ text: "Walking" });
  });
  it("keeps reconnect choices private until mutual and supports withdrawal", async () => {
    const id = await propose(); await confirm(id);
    expect((await call(alice,"PUT",`/v1/plans/${id}/reconnect`,{ targetId: bob, activity: "Walk", interested: true })).statusCode).toBe(409);
    now = new Date("2026-10-19T12:00:00Z");
    expect((await call(alice,"PUT",`/v1/plans/${id}/reconnect`,{ targetId: bob, activity: "Walk", interested: true })).json()).toMatchObject({ mutual: false });
    expect(await db.notification.count({ where: { userId: bob, type: "MEET_AGAIN" } })).toBe(0);
    expect((await call(bob,"PUT",`/v1/plans/${id}/reconnect`,{ targetId: alice, activity: "Books", interested: true })).json()).toMatchObject({ mutual: true });
    await call(alice,"PUT",`/v1/plans/${id}/reconnect`,{ targetId: bob, activity: "Withdraw", interested: false });
    expect(await db.notification.count({ where: { userId: bob, type: "MEET_AGAIN" } })).toBe(0);
  });
  it("limits circle capacity under concurrent joins and enforces removal", async () => {
    const result = await call(alice,"POST","/v1/circles",{ name: "Book friends", interest: "Books", rules: "Be kind and respect each other", capacity: 2 });
    const id = result.json<{ id: string }>().id;
    const results = await Promise.all([bob,outsider].map(user => call(user,"PUT",`/v1/circles/${id}/membership`,{ joined: true, acceptRules: true })));
    expect(results.map(r => r.statusCode).sort()).toEqual([200,409]);
    const joined = results[0]?.statusCode === 200 ? bob : outsider;
    expect((await call(joined,"POST",`/v1/circles/${id}/posts`,{ body: "Hello readers" })).statusCode).toBe(200);
    await call(alice,"DELETE",`/v1/circles/${id}/members/${joined}`);
    expect((await call(joined,"PUT",`/v1/circles/${id}/membership`,{ joined: true, acceptRules: true })).statusCode).toBe(403);
    expect((await call(joined,"GET",`/v1/circles/${id}/posts`)).statusCode).toBe(403);
  });
  it("enforces photo audience, withdrawal and membership", async () => {
    const id = await propose(); await confirm(id); now = new Date("2026-10-19T12:00:00Z");
    const imageBase64 = Buffer.from([255,216,255,192,0,11,8,0,1,0,1,1,1,17,0,255,218,0,8,1,1,0,0,63,0,0,255,217]).toString("base64");
    const response = await call(alice,"POST",`/v1/plans/${id}/memories`,{ caption: "Coffee", imageBase64, audience: "PRIVATE", consent: true });
    const memory = response.json<{ id: string }>().id;
    expect(response.statusCode).toBe(200);
    expect((await call(bob,"GET",`/v1/memories/${memory}/image`)).statusCode).toBe(404);
    expect((await call(outsider,"GET",`/v1/plans/${id}/memories`)).statusCode).toBe(403);
    await call(alice,"PUT",`/v1/memories/${memory}/audience`,{ audience: "GROUP", consent: true });
    expect((await call(bob,"GET",`/v1/memories/${memory}/image`)).statusCode).toBe(200);
    await call(alice,"PUT",`/v1/memories/${memory}/audience`,{ audience: "PRIVATE", consent: true });
    expect((await call(bob,"GET",`/v1/memories/${memory}/image`)).statusCode).toBe(404);
    expect((await call(bob,"DELETE",`/v1/memories/${memory}`)).statusCode).toBe(403);
  });
  it("rejects inactive accounts and does not subscribe users by default", async () => {
    expect((await call(alice,"GET","/v1/home")).json()).toMatchObject({ weeklyDigest: false });
    await deliverWeeklyDigests(db,now);
    expect(await db.notification.count({ where: { userId: alice, type: "WEEKLY_DIGEST" } })).toBe(0);
    await db.user.update({ where: { id: alice }, data: { status: "DELETION_PENDING" } });
    expect((await call(alice,"PUT","/v1/me/up-for",{ activity: "Coffee" })).statusCode).toBe(403);
  });
});
