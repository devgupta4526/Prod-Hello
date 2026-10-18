import { describe, expect, it } from "vitest";
import { parseEnvironment } from "../src/config/env.js";
import { testEnvironment } from "./environment.js";

describe("Blueprint 15.6 startup safety", () => {
  it("allows staging console with NODE_ENV production", () => {
    expect(
      parseEnvironment({
        ...testEnvironment(),
        APP_ENV: "staging",
        NODE_ENV: "production",
        ADMIN_ORIGIN: "https://admin.example.com",
      }).APP_ENV,
    ).toBe("staging");
  });
  it.each(["JWT_ACCESS_SECRET", "JWT_REFRESH_PEPPER", "OTP_HASH_PEPPER"])(
    "rejects short %s without leaking its value",
    (key) => {
      expect(() =>
        parseEnvironment({
          ...testEnvironment(),
          [key]: "sensitive-short-value",
        }),
      ).toThrow(`Invalid configuration: ${key}`);
    },
  );
  it("rejects console in production", () => {
    expect(() =>
      parseEnvironment({
        ...testEnvironment(),
        APP_ENV: "production",
        ADMIN_ORIGIN: "https://admin.example.com",
      }),
    ).toThrow("OTP_PROVIDER");
  });
  it("rejects console fallback in production", () => {
    expect(() =>
      parseEnvironment({
        ...testEnvironment(),
        APP_ENV: "production",
        ADMIN_ORIGIN: "https://admin.example.com",
        OTP_PROVIDER: "msg91",
        OTP_FALLBACK_PROVIDER: "console",
        MSG91_AUTH_KEY: "test",
        MSG91_TEMPLATE_ID: "test",
      }),
    ).toThrow("OTP_PROVIDER");
  });
  it.each(["msg91", "twilio"])(
    "rejects missing %s credentials including fallback",
    (provider) => {
      expect(() =>
        parseEnvironment({ ...testEnvironment(), OTP_PROVIDER: provider }),
      ).toThrow("OTP_PROVIDER");
      expect(() =>
        parseEnvironment({
          ...testEnvironment(),
          OTP_FALLBACK_PROVIDER: provider,
        }),
      ).toThrow("OTP_PROVIDER");
    },
  );
  it.each([
    "919999900001:123456",
    "+919999900001:123",
    "+919999900001:123456,+919999900001:654321",
  ])("rejects invalid sandbox configuration", (entries) => {
    expect(() =>
      parseEnvironment({ ...testEnvironment(), OTP_SANDBOX_NUMBERS: entries }),
    ).toThrow("OTP_SANDBOX_NUMBERS");
  });
  it("enforces staging and production sandbox caps", () => {
    const entries = Array.from(
      { length: 11 },
      (_, i) => `+9199999000${String(i).padStart(2, "0")}:123456`,
    );
    expect(() =>
      parseEnvironment({
        ...testEnvironment(),
        OTP_SANDBOX_NUMBERS: entries.join(","),
      }),
    ).toThrow("OTP_SANDBOX_NUMBERS");
    expect(() =>
      parseEnvironment({
        ...testEnvironment(),
        APP_ENV: "production",
        ADMIN_ORIGIN: "https://admin.example.com",
        OTP_PROVIDER: "msg91",
        MSG91_AUTH_KEY: "test",
        MSG91_TEMPLATE_ID: "test",
        OTP_SANDBOX_NUMBERS: entries.slice(0, 4).join(","),
      }),
    ).toThrow("OTP_SANDBOX_NUMBERS");
  });
});
