import { defineConfig } from "prisma/config";
import { readMigrationUrl } from "./src/config/env.js";
export default defineConfig({
  schema: "prisma/schema.prisma",
  migrations: { path: "prisma/migrations" },
  datasource: { url: readMigrationUrl() },
});
