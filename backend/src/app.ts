import { createHmac, randomUUID } from "node:crypto";
import Fastify, { LogController, type FastifyError } from "fastify";
import type { Writable } from "node:stream";
import helmet from "@fastify/helmet";
import cors from "@fastify/cors";
import swagger from "@fastify/swagger";
import {
  serializerCompiler,
  validatorCompiler,
  jsonSchemaTransform,
} from "fastify-type-provider-zod";
import type { Environment } from "./config/env.js";
import type { Database } from "./lib/database.js";
import { SystemRepository } from "./modules/system/system.repo.js";
import { SystemService } from "./modules/system/system.service.js";
import { systemRoutes } from "./modules/system/system.routes.js";
import { AuthRepository } from "./modules/auth/auth.repo.js";
import { AuthService, type Identity } from "./modules/auth/auth.service.js";
import { authRoutes } from "./modules/auth/auth.routes.js";
import { SafetyRepository } from "./modules/safety/safety.repo.js";
import { safetyRoutes } from "./modules/safety/safety.routes.js";
import { DiscoveryRepository } from "./modules/discovery/discovery.repo.js";
import { DiscoveryService } from "./modules/discovery/discovery.service.js";
import { discoveryRoutes } from "./modules/discovery/discovery.routes.js";
import { HelloRepository } from "./modules/hello/hello.repo.js";
import { HelloService } from "./modules/hello/hello.service.js";
import { helloRoutes } from "./modules/hello/hello.routes.js";
import { ChatRepository } from "./modules/chat/chat.repo.js";
import { ChatService } from "./modules/chat/chat.service.js";
import { chatRoutes } from "./modules/chat/chat.routes.js";
import { supportRoutes } from "./modules/social/support.routes.js";
import { voiceRoutes } from "./modules/chat/voice.routes.js";
import { PoolsRepository } from "./modules/pools/pools.repo.js";
import { PoolsService } from "./modules/pools/pools.service.js";
import { poolsRoutes } from "./modules/pools/pools.routes.js";
import { EventsRepository } from "./modules/events/events.repo.js";
import { EventsService } from "./modules/events/events.service.js";
import { eventsRoutes } from "./modules/events/events.routes.js";
import { OrdersRepository } from "./modules/orders/orders.repo.js";
import { OrdersService } from "./modules/orders/orders.service.js";
import { ordersRoutes } from "./modules/orders/orders.routes.js";
import { NotificationsRepository } from "./modules/notifications/notifications.repo.js";
import { notificationsRoutes } from "./modules/notifications/notifications.routes.js";
import { socialRoutes } from "./modules/social/social.routes.js";
import { meRoutes } from "./modules/me/me.routes.js";
import { systemClock } from "./lib/clock.js";
import { AppError } from "./lib/app-error.js";
import { smsWebhooks } from "./providers/sms/webhooks.js";

declare module "fastify" {
  interface FastifyContextConfig {
    access?: "public" | "session" | "onboarding";
    returnsPeople?: boolean;
  }
  interface FastifyRequest {
    identity: Identity | null;
  }
}

export async function buildApp(
  env: Environment,
  database: Database,
  logStream?: Writable,
) {
  const app = Fastify({
    bodyLimit: 102400,
    trustProxy: false,
    requestIdHeader: false,
    genReqId: () => randomUUID(),
    logController: new LogController({ disableRequestLogging: true }),
    logger: {
      ...(logStream ? { stream: logStream } : {}),
      level: "info",
      redact: {
        paths: [
          "authorization",
          "code",
          "phone",
          "refreshToken",
          "location",
          "lat",
          "lng",
          "venueLat",
          "venueLng",
          "req.headers.authorization",
          "req.headers.cookie",
        ],
        remove: true,
      },
      serializers: {
        req: () => ({}),
        res: () => ({}),
        err: () => ({ type: "Error", message: "Redacted", stack: "" }),
      },
    },
  });

  app.setValidatorCompiler(validatorCompiler);
  app.setSerializerCompiler(serializerCompiler);
  await app.register(helmet);
  await app.register(cors, { origin: env.ADMIN_ORIGIN, strictPreflight: true });
  await app.register(swagger, {
    openapi: { info: { title: "Hello API", version: "0.2.0" } },
    transform: jsonSchemaTransform,
  });

  // Verify explicit access and people policy for all application routes
  app.addHook("onRoute", (route) => {
    if (
      !route.config?.access ||
      typeof route.config.returnsPeople !== "boolean"
    ) {
      throw new Error(
        "Application route requires explicit access and people policy",
      );
    }
  });

  const repository = new SystemRepository(database);
  const authRepository = new AuthRepository(database);
  const auth = new AuthService(authRepository, env, systemClock);
  const safetyRepo = new SafetyRepository(database);
  const discoveryRepo = new DiscoveryRepository(database);
  const discoveryService = new DiscoveryService(
    discoveryRepo,
    safetyRepo,
    env,
    systemClock,
  );
  const helloRepo = new HelloRepository(database);
  const helloService = new HelloService(
    helloRepo,
    safetyRepo,
    discoveryRepo,
    systemClock,
  );
  const chatRepo = new ChatRepository(database);
  const chatService = new ChatService(chatRepo, safetyRepo, systemClock);
  const poolsRepo = new PoolsRepository(database);
  const poolsService = new PoolsService(
    poolsRepo,
    safetyRepo,
    discoveryRepo,
    systemClock,
  );
  const eventsRepo = new EventsRepository(database);
  const eventsService = new EventsService(eventsRepo, systemClock);
  const ordersRepo = new OrdersRepository(database);
  const ordersService = new OrdersService(ordersRepo, env, systemClock);
  const notificationsRepo = new NotificationsRepository(database);

  app.decorateRequest("identity", null);

  app.addHook("onRequest", async (request, reply) => {
    reply.header("x-request-id", request.id);
    if (request.routeOptions.url === "/health" || request.method === "OPTIONS")
      return;

    const key = createHmac("sha256", env.JWT_REFRESH_PEPPER)
      .update(`${request.routeOptions.url ?? "unmatched"}:${request.ip}`)
      .digest("hex");
    const result = await repository.consume(key);
    if (!result.allowed) {
      reply.header("retry-after", result.retryAfter);
      return reply.code(429).send({
        error: {
          code: "rate_limited",
          message: "Please retry later.",
          requestId: request.id,
        },
      });
    }
  });

  app.addHook("preHandler", async (request) => {
    const access = request.routeOptions.config.access;
    if (!access || access === "public") return;

    request.identity = await auth.authenticate(request.headers.authorization);
    const user = request.identity.user;

    if (access === "onboarding") {
      if (user.status !== "ONBOARDING" && user.status !== "ACTIVE") {
        throw new AppError(
          user.status === "SUSPENDED" ? "account_suspended" : "forbidden",
          403,
        );
      }
      if (request.routeOptions.url !== "/v1/me/dob" && !user.adultVerifiedAt) {
        throw new AppError("onboarding_incomplete", 409);
      }
    } else if (access === "session") {
      if (user.status === "SUSPENDED" && request.routeOptions.url !== "/v1/me/appeals") {
        throw new AppError("account_suspended", 403);
      }
    }
  });

  app.addHook("onResponse", async (request, reply) => {
    app.log.info(
      {
        requestId: request.id,
        method: request.method,
        route: request.routeOptions.url ?? "unmatched",
        statusCode: reply.statusCode,
      },
      "request completed",
    );
  });

  app.setErrorHandler<FastifyError>((error, request, reply) => {
    if (error instanceof AppError) {
      if (error.retryAfter) reply.header("retry-after", error.retryAfter);
      return reply.code(error.httpStatus).send({
        error: {
          code: error.code,
          message: "The request could not be completed.",
          requestId: request.id,
        },
      });
    }
    const status =
      error.validation || error.statusCode === 400
        ? 400
        : error.statusCode === 413
          ? 413
          : error.statusCode === 415
            ? 415
            : 503;
    const code =
      status === 400
        ? "validation_failed"
        : status === 413
          ? "payload_too_large"
          : status === 415
            ? "unsupported_media_type"
            : "service_unavailable";
    return reply.code(status).send({
      error: {
        code,
        message:
          status === 503
            ? "Service temporarily unavailable."
            : "Invalid request.",
        requestId: request.id,
      },
    });
  });

  app.setNotFoundHandler((request, reply) =>
    reply.code(404).send({
      error: {
        code: "not_found",
        message: "Not found.",
        requestId: request.id,
      },
    }),
  );

  // Wire all routes
  systemRoutes(
    app,
    new SystemService(repository, env.MIN_APP_VERSION, env.MAINTENANCE_MODE),
  );
  authRoutes(app, auth);
  safetyRoutes(app, safetyRepo, database, systemClock);
  discoveryRoutes(app, discoveryService);
  helloRoutes(app, helloService);
  chatRoutes(app, chatService);
  supportRoutes(app, database);
  voiceRoutes(app, database, chatService, discoveryService);
  poolsRoutes(app, poolsService);
  eventsRoutes(app, eventsService);
  ordersRoutes(app, ordersService);
  notificationsRoutes(app, notificationsRepo);
  meRoutes(app, database, systemClock);
  socialRoutes(app, database, systemClock, discoveryService);
  await smsWebhooks(app, env, database);

  return app;
}
