import { parseEnvironment } from "../src/config/env.js";
export function testEnvironment(
  databaseUrl = "postgresql://test:test@localhost:5432/test",
) {
  return parseEnvironment({
    APP_ENV: "local",
    NODE_ENV: "test",
    DATABASE_URL: databaseUrl,
    DIRECT_URL: databaseUrl,
    JWT_ACCESS_SECRET: "test-access-only-".repeat(3),
    JWT_REFRESH_PEPPER: "test-refresh-only-".repeat(3),
    OTP_HASH_PEPPER: "test-otp-only-".repeat(3),
    OTP_PROVIDER: "console",
    ADMIN_ORIGIN: "http://localhost:5173",
  });
}
