import { execFileSync } from "node:child_process";
import { Writable } from "node:stream";
import {
  PostgreSqlContainer,
  type StartedPostgreSqlContainer,
} from "@testcontainers/postgresql";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { FastifyInstance } from "fastify";
import { createDatabase, type Database } from "../src/lib/database.js";
import { buildApp } from "../src/app.js";
import { testEnvironment } from "./environment.js";
import { configSchema } from "../src/modules/system/system.schemas.js";

let container: StartedPostgreSqlContainer;
let database: Database;
let app: FastifyInstance;
const cleanup: (() => Promise<unknown>)[] = [];
beforeAll(async () => {
  container = await new PostgreSqlContainer("postgis/postgis:17-3.5").start();
  cleanup.unshift(() => container.stop());
  const url = container.getConnectionUri();
  execFileSync(
    process.execPath,
    ["node_modules/prisma/build/index.js", "migrate", "deploy"],
    {
      env: { ...process.env, DIRECT_URL: url },
      stdio: "pipe",
    },
  );
  database = createDatabase(url);
  cleanup.unshift(() => database.$disconnect());
  app = await buildApp(testEnvironment(url), database);
  cleanup.unshift(() => app.close());
  await app.ready();
}, 180000);
afterAll(async () => {
  for (const close of cleanup) await close();
});

describe("real PostgreSQL foundation", () => {
  it("BR-10 logs exclude personal data in URLs, headers and request bodies", async () => {
    const chunks: string[] = [];
    const stream = new Writable({
      write(chunk: Buffer, _encoding, callback) {
        chunks.push(chunk.toString());
        callback();
      },
    });
    const logged = await buildApp(
      testEnvironment(container.getConnectionUri()),
      database,
      stream,
    );
    try {
      await logged.inject({
        method: "GET",
        url: "/health?phone=private-phone&lat=12.34567",
        headers: {
          authorization: "Bearer private-token",
          cookie: "private-cookie",
        },
      });
      await logged.inject({
        method: "POST",
        url: "/private-path",
        payload: { location: { lat: 12.34567 }, code: "private-code" },
      });
      const output = chunks.join("");
      expect(output).toContain("request completed");
      expect(output).not.toMatch(
        /private-phone|12\.34567|private-token|private-cookie|private-path|private-code/,
      );
    } finally {
      await logged.close();
    }
  });
  it("health checks a real database and sets security headers", async () => {
    const response = await app.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    expect(response.json<unknown>()).toEqual({ status: "ok" });
    expect(response.headers["x-content-type-options"]).toBe("nosniff");
  });
  it("BR-10 config exposes only public configuration, with future features disabled", async () => {
    const response = await app.inject({ method: "GET", url: "/v1/config" });
    const body = configSchema.parse(response.json<unknown>());
    expect(body.features).toEqual({
      purchasesEnabled: false,
      helloEnabled: false,
      websocketEnabled: false,
    });
    expect(response.body).not.toMatch(
      /phone|token|secret|latitude|longitude|location/i,
    );
  });
  it("BR-10 output schema strips unexpected sensitive fields", () => {
    const result = configSchema.parse({
      minAppVersion: 1,
      features: {
        purchasesEnabled: false,
        helloEnabled: false,
        websocketEnabled: false,
      },
      limits: {
        discoveryRadiusMaxKm: 25,
        helloDailyCap: 20,
        helloNewAccountDailyCap: 5,
      },
      location: { lat: 12.345, lng: 45.678 },
    });
    expect(result).not.toHaveProperty("location");
  });
  it("BR-02 database prevents duplicate phone identity", async () => {
    await database.user.create({
      data: {
        id: "0199aa00-0000-7000-8000-000000000001",
        phoneE164: "+919999900001",
      },
    });
    await expect(
      database.user.create({
        data: {
          id: "0199aa00-0000-7000-8000-000000000002",
          phoneE164: "+919999900001",
        },
      }),
    ).rejects.toMatchObject({ code: "P2002" });
  });
  it("BR-11 block storage rejects self-blocks and duplicate pairs; both directions can be queried", async () => {
    const a = "0199aa00-0000-7000-8000-000000000003";
    const b = "0199aa00-0000-7000-8000-000000000004";
    await database.user.createMany({
      data: [
        { id: a, phoneE164: "+919999900003" },
        { id: b, phoneE164: "+919999900004" },
      ],
    });
    await expect(
      database.block.create({
        data: {
          id: "0199aa00-0000-7000-8000-000000000005",
          blockerId: a,
          blockedId: a,
        },
      }),
    ).rejects.toThrow();
    await database.block.create({
      data: {
        id: "0199aa00-0000-7000-8000-000000000006",
        blockerId: a,
        blockedId: b,
      },
    });
    await expect(
      database.block.create({
        data: {
          id: "0199aa00-0000-7000-8000-000000000007",
          blockerId: a,
          blockedId: b,
        },
      }),
    ).rejects.toMatchObject({ code: "P2002" });
    expect(
      await database.block.count({
        where: {
          OR: [
            { blockerId: a, blockedId: b },
            { blockerId: b, blockedId: a },
          ],
        },
      }),
    ).toBe(1);
  });
  it("shares atomic rate limits across concurrent API instances and survives app recreation", async () => {
    const second = await buildApp(
      testEnvironment(container.getConnectionUri()),
      database,
    );
    try {
      const responses = await Promise.all(
        Array.from({ length: 65 }, (_, i) =>
          (i % 2 ? app : second).inject({
            method: "GET",
            url: "/v1/config",
            remoteAddress: "192.0.2.50",
          }),
        ),
      );
      expect(responses.filter((r) => r.statusCode === 200)).toHaveLength(60);
      expect(responses.filter((r) => r.statusCode === 429)).toHaveLength(5);
      const limited = await second.inject({
        method: "GET",
        url: "/v1/config",
        remoteAddress: "192.0.2.50",
      });
      expect(limited.statusCode).toBe(429);
      expect(Number(limited.headers["retry-after"])).toBeGreaterThan(0);
    } finally {
      await second.close();
    }
  });
  it("refuses undeclared or people-returning routes", async () => {
    const guarded = await buildApp(
      testEnvironment(container.getConnectionUri()),
      database,
    );
    expect(() => guarded.get("/accidental", () => ({ ok: true }))).toThrow(
      "explicit access",
    );
    await guarded.close();
  });
  it("does not permit an unapproved web origin", async () => {
    const response = await app.inject({
      method: "OPTIONS",
      url: "/v1/config",
      headers: {
        origin: "https://evil.example",
        "access-control-request-method": "GET",
      },
    });
    expect(response.headers["access-control-allow-origin"]).not.toBe(
      "https://evil.example",
    );
  });
  it("returns a sanitized 503 when the database is unavailable", async () => {
    const unavailable = createDatabase(
      "postgresql://private:secret@127.0.0.1:1/unavailable",
    );
    const unhealthy = await buildApp(testEnvironment(), unavailable);
    try {
      const response = await unhealthy.inject({
        method: "GET",
        url: "/health",
      });
      expect(response.statusCode).toBe(503);
      expect(response.body).not.toMatch(/private|secret|postgres|stack|SELECT/);
    } finally {
      await unhealthy.close();
      await unavailable.$disconnect();
    }
  });
});
