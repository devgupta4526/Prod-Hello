import type { Database } from "../../lib/database.js";
export class SystemRepository {
  constructor(private readonly db: Database) {}
  async ready(): Promise<void> {
    await this.db.$queryRaw`SELECT 1`;
  }
  async consume(
    key: string,
  ): Promise<{ allowed: boolean; retryAfter: number }> {
    // The database clock and atomic upsert make concurrent instances share one window.
    const rows = await this.db.$queryRaw<
      { count: number; retryAfter: number }[]
    >`
      INSERT INTO "RateLimitBucket" (key, "windowStart", count)
      VALUES (${key}, date_trunc('minute', now()), 1)
      ON CONFLICT (key, "windowStart") DO UPDATE SET count = LEAST("RateLimitBucket".count + 1, 61)
      RETURNING count, GREATEST(1, CEIL(EXTRACT(EPOCH FROM ("windowStart" + interval '1 minute' - now()))))::int AS "retryAfter"`;
    const row = rows[0];
    if (!row) throw new Error("Rate limit unavailable");
    return { allowed: row.count <= 60, retryAfter: row.retryAfter };
  }
  async cleanup(): Promise<void> {
    await this.db
      .$executeRaw`DELETE FROM "RateLimitBucket" WHERE "windowStart" < now() - interval '1 hour'`;
  }
}
