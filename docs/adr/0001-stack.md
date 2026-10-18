# ADR-001: Foundation stack and existing project preservation

Date: 2026-10-17. Status: proposed implementation, awaiting slice review.

Use the blueprint's Node LTS, Fastify, Zod, Prisma and PostgreSQL stack. Pin dependencies
and lock transitive versions. Node installed locally is 24.14.0. Registry verification
reported Prisma's latest tag as 8.0.0-rc.23; select stable 7.10.1, not a prerelease.
Prisma 7 uses @prisma/adapter-pg and prisma.config.ts; runtime uses DATABASE_URL,
migrations use DIRECT_URL. Generated client output is ignored and regenerated in CI.

Official compatibility reference:
https://www.prisma.io/docs/guides/upgrade-prisma-orm/v7
https://fastify.dev/docs/latest/Reference/LTS/

Preserve existing Romear/com.example.romear Android project until the Android foundation
slice. Hello remains a documentation placeholder (OD-2); no package rename is performed.
Supplementary ConnectSphere documents are inspiration only; conflicting behavior does
not override the blueprint. Phases 0–2 are requested; review pauses remain per slice.

Use a PostgreSQL atomic fixed-window limiter for the current config endpoint, not a
memory-only rate-limit store. This adds RateLimitBucket as infrastructure storage.
No Redis or additional product feature is introduced.
