import type { FastifyInstance } from "fastify";
import type { ZodTypeProvider } from "fastify-type-provider-zod";
import type { SystemService } from "./system.service.js";
import { configSchema, errorSchema, healthSchema } from "./system.schemas.js";
export function systemRoutes(app: FastifyInstance, service: SystemService) {
  const router = app.withTypeProvider<ZodTypeProvider>();
  router.get(
    "/health",
    {
      config: { access: "public", returnsPeople: false },
      schema: {
        operationId: "getHealth",
        response: { 200: healthSchema, 503: errorSchema },
      },
    },
    async () => service.health(),
  );
  router.get(
    "/v1/config",
    {
      config: { access: "public", returnsPeople: false },
      schema: {
        operationId: "getConfig",
        response: { 200: configSchema, 429: errorSchema, 503: errorSchema },
      },
    },
    () => service.config(),
  );
}
