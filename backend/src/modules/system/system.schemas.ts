import { z } from "zod";
export const healthSchema = z.object({ status: z.literal("ok") });
export const configSchema = z.object({
  minAppVersion: z.number().int(),
  maintenance: z.boolean(),
  features: z.object({
    purchasesEnabled: z.boolean(),
    helloEnabled: z.boolean(),
    websocketEnabled: z.boolean(),
  }),
  limits: z.object({
    discoveryRadiusMaxKm: z.number().int(),
    helloDailyCap: z.number().int(),
    helloNewAccountDailyCap: z.number().int(),
  }),
});
export const errorSchema = z.object({
  error: z.object({
    code: z.string(),
    message: z.string(),
    requestId: z.string(),
  }),
});
