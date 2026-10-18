import { z } from "zod";

const secret = z
  .string()
  .refine(
    (value) => Buffer.byteLength(value, "utf8") >= 32,
    "Must be at least 32 bytes",
  );
const provider = z.enum(["console", "msg91", "twilio"]);
const databaseUrl = z
  .url()
  .refine((value) =>
    ["postgres:", "postgresql:"].includes(new URL(value).protocol),
  );
const schema = z
  .object({
    APP_ENV: z.enum(["local", "staging", "production"]),
    NODE_ENV: z.enum(["development", "test", "production"]),
    PORT: z.coerce.number().int().min(1).max(65535).default(3000),
    MIN_APP_VERSION: z.coerce.number().int().positive().default(1),
    MAINTENANCE_MODE: z.union([z.boolean(), z.enum(["true", "false"])]).default("false").transform((value) => typeof value === "boolean" ? value : value === "true"),
    DATABASE_URL: databaseUrl,
    DIRECT_URL: databaseUrl,
    JWT_ACCESS_SECRET: secret,
    JWT_REFRESH_PEPPER: secret,
    OTP_HASH_PEPPER: secret,
    OTP_PROVIDER: provider,
    OTP_FALLBACK_PROVIDER: provider.optional(),
    OTP_SANDBOX_NUMBERS: z.string().default(""),
    OTP_DAILY_SMS_BUDGET: z.coerce.number().int().nonnegative().default(0),
    OTP_ALLOWED_COUNTRIES: z.string().default("IN"),
    SMS_APP_HASH: z.string().regex(/^[A-Za-z0-9+/]{11}$/).optional(),
    CLOUDINARY_CLOUD_NAME: z.string().regex(/^[a-zA-Z0-9_-]+$/).optional(),
    CLOUDINARY_API_KEY: z.string().min(1).optional(),
    CLOUDINARY_API_SECRET: z.string().min(1).optional(),
    ADMIN_ORIGIN: z
      .url()
      .refine(
        (value) => new URL(value).origin === value,
        "Use an origin without a path",
      ),
    MSG91_AUTH_KEY: z.string().min(1).optional(),
    MSG91_TEMPLATE_ID: z.string().min(1).optional(),
    TWILIO_ACCOUNT_SID: z.string().min(1).optional(),
    TWILIO_AUTH_TOKEN: z.string().min(1).optional(),
    TWILIO_FROM: z.string().min(1).optional(),
    TWILIO_STATUS_CALLBACK_URL: z.url().refine((value) => new URL(value).protocol === "https:").optional(),
  })
  .superRefine((value, context) => {
    const fail = (path: string, message: string) => {
      context.addIssue({ code: "custom", path: [path], message });
    };
    const selected = [value.OTP_PROVIDER, value.OTP_FALLBACK_PROVIDER];
    const cloudinaryFields = [value.CLOUDINARY_CLOUD_NAME, value.CLOUDINARY_API_KEY, value.CLOUDINARY_API_SECRET];
    if (cloudinaryFields.some(Boolean) && !cloudinaryFields.every(Boolean)) fail("CLOUDINARY_API_SECRET", "All Cloudinary settings must be supplied together");
    if (value.APP_ENV === "production" && selected.includes("console"))
      fail(
        "OTP_PROVIDER",
        "Console is forbidden in production, including fallback",
      );
    if (
      value.APP_ENV !== "local" &&
      new URL(value.ADMIN_ORIGIN).protocol !== "https:"
    )
      fail("ADMIN_ORIGIN", "HTTPS required outside local");
    const entries =
      value.OTP_SANDBOX_NUMBERS === ""
        ? []
        : value.OTP_SANDBOX_NUMBERS.split(",");
    if (entries.length > (value.APP_ENV === "production" ? 3 : 10))
      fail("OTP_SANDBOX_NUMBERS", "Too many sandbox entries");
    if (entries.some((entry) => !/^\+[1-9]\d{7,14}:\d{6}$/.test(entry)))
      fail(
        "OTP_SANDBOX_NUMBERS",
        "Expected E.164:number with a six-digit code",
      );
    if (
      new Set(entries.map((entry) => entry.split(":")[0])).size !==
      entries.length
    )
      fail("OTP_SANDBOX_NUMBERS", "Duplicate sandbox phone");
    if (
      selected.includes("msg91") &&
      (!value.MSG91_AUTH_KEY || !value.MSG91_TEMPLATE_ID)
    )
      fail("OTP_PROVIDER", "MSG91 credentials required");
    if (
      selected.includes("twilio") &&
      (!value.TWILIO_ACCOUNT_SID ||
        !value.TWILIO_AUTH_TOKEN ||
        !value.TWILIO_FROM)
    )
      fail("OTP_PROVIDER", "Twilio credentials required");
  });

export type Environment = z.infer<typeof schema>;
export function parseEnvironment(input: Record<string, unknown>): Environment {
  const result = schema.safeParse(input);
  if (!result.success) {
    // Never include values or raw Zod diagnostics in startup logs.
    throw new Error(
      `Invalid configuration: ${[...new Set(result.error.issues.map((issue) => issue.path.join(".")))].join(", ")}`,
    );
  }
  return result.data;
}
export function readEnvironment(): Environment {
  return parseEnvironment(process.env);
}
export function readMigrationUrl(): string {
  return databaseUrl.parse(process.env.DIRECT_URL ?? process.env.DATABASE_URL ?? "postgresql://hello:hello_local_only@localhost:5432/hello");
}
