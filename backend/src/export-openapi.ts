import { writeFile } from "node:fs/promises";
import { parseEnvironment } from "./config/env.js";
import { createDatabase } from "./lib/database.js";
import { buildApp } from "./app.js";
// Contract generation connects to no services. These are explicitly non-runtime fixtures.
const env = parseEnvironment({
  APP_ENV: "local",
  NODE_ENV: "test",
  DATABASE_URL: "postgresql://contract:contract@localhost:5432/contract",
  DIRECT_URL: "postgresql://contract:contract@localhost:5432/contract",
  JWT_ACCESS_SECRET: "contract-only-".repeat(4),
  JWT_REFRESH_PEPPER: "contract-only-".repeat(4),
  OTP_HASH_PEPPER: "contract-only-".repeat(4),
  OTP_PROVIDER: "console",
  ADMIN_ORIGIN: "http://localhost:5173",
});
const database = createDatabase(env.DATABASE_URL);
const app = await buildApp(env, database);
try {
  await app.ready();
  await writeFile(
    new URL("../../docs/openapi.json", import.meta.url),
    `${JSON.stringify(app.swagger(), null, 2)}\n`,
  );
} finally {
  await app.close();
  await database.$disconnect();
}
