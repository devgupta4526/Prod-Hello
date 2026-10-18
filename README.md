# Hello / existing Romear Android workspace

Blueprint: Hello_Pool_Events_Blueprint_v2.md. Current status: docs/PROGRESS.md.
Feature gaps and release priorities: [docs/FEATURE_BACKLOG.md](docs/FEATURE_BACKLOG.md).
Social feature implementation and deferred verification: [docs/SOCIAL_FEATURES.md](docs/SOCIAL_FEATURES.md).
UI, interaction, motion and haptic direction: [docs/EXPERIENCE_DESIGN.md](docs/EXPERIENCE_DESIGN.md).
Android remains at app/ with its existing Gradle setup. It has not been renamed or migrated.
Backend source is in backend/. Phases 0â€“2 now include authentication and onboarding,
Cloudinary photo upload, rotating sessions, Android XML screens, secure token storage,
session management and a debug design gallery. Phase exit gates have not passed;
see docs/PROGRESS.md for verification and gaps.

Cloudinary is the selected media provider. Configure CLOUDINARY_CLOUD_NAME,
CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET on the backend only. Android receives
a signed upload request; the backend verifies the uploaded resource before confirming it.
Real SMS is disabled by the default daily budget of zero. Configure provider credentials
and an intentional budget, or use explicitly owned sandbox numbers for development.

## Local backend (PowerShell, from E:/Production-Level-Apps)

Prerequisites: Node 24.14.0, npm, running Docker Desktop with Linux containers.

```powershell
docker compose up -d postgres
Set-Location backend
npm ci
Copy-Item .env.example .env
```

Fill JWT_ACCESS_SECRET, JWT_REFRESH_PEPPER and OTP_HASH_PEPPER in .env with separate
cryptographically random values (at least 32 bytes). The local database password in
compose.yaml is for the loopback-only development database, never staging/production.

```powershell
node --env-file=.env node_modules/prisma/build/index.js generate
npm run db:deploy
npm run dev
```

GET http://localhost:3000/health and http://localhost:3000/v1/config. Config reports future
features disabled. For a built server use npm run build, then node --env-file=.env dist/server.js.

## Verification

```powershell
$env:DIRECT_URL = 'postgresql://contract:contract@localhost:5432/contract'
npm run db:generate
npm run lint
npm run format:check
npm run typecheck
npm run build
npm test
npm run openapi
npm audit --audit-level=high
```

Tests create their own disposable PostgreSQL/PostGIS container and apply migrations.
They never truncate or drop the local application database. DIRECT_URL above is only
for code generation; integration tests supply their own container URL for migrations.

## Free public API deployment

Deploy the backend using [the Render Blueprint](https://dashboard.render.com/select-repo?type=blueprint).
Select `devgupta4526/Prod-Hello`; Render reads the root `render.yaml`, which selects
the free web service plan. The Android app is built separately.

1. Create a free Neon PostgreSQL project and an empty database for this test deployment.
2. Set `DATABASE_URL` to the pooled connection string and `DIRECT_URL` to the direct
   connection string, both with the SSL settings Neon supplies. Keep these values in
   Render's dashboard, never GitHub.
3. Set `ADMIN_ORIGIN` to the HTTPS origin of your browser test client (no trailing
   slash or path). This controls browser CORS; native Android and curl do not need it.
4. Set `OTP_SANDBOX_NUMBERS` to your own test phone and a six-digit code, in the format
   `+<country-code><phone>:<six-digit-code>`. This grants test login access, so keep
   it private. Real SMS remains disabled. Photo uploads need your own Cloudinary
   credentials, configured together in Render.
5. Deploy. Render generates the three secrets and runs database migrations at startup.
   Copy the actual public service URL from the dashboard and verify:

```powershell
$api = 'https://YOUR-SERVICE.onrender.com'
Invoke-RestMethod "$api/health"
Invoke-RestMethod "$api/v1/config"
```

For Android staging, build with the actual URL, including its trailing slash:

```powershell
.\gradlew.bat assembleStaging -Phello.stagingUrl=https://YOUR-SERVICE.onrender.com/
```

Render's free web service sleeps after 15 minutes without traffic and wakes on the
next request; allow extra time for the first request. Neon has free usage limits.
Use Neon rather than Render's free database if you need to avoid Render's 30-day
free database expiry. See [Render free limits](https://render.com/docs/free) and
[Neon plans](https://neon.com/pricing).

### Deployment verification

Create GitHub repository and push the reviewed source. Create Neon staging branch and
verify PostGIS availability; test migration on a disposable Neon branch first. Import
render.yaml as a Render Blueprint; set pooled DATABASE_URL, direct DIRECT_URL,
ADMIN_ORIGIN (HTTPS), and sandbox entries in the dashboard. Render generates secrets.
Deploy manually after review. Build command: npm ci --include=dev && npm run db:generate
&& npm run build. Start: npx --no-install prisma migrate deploy && npm start.
Never put dashboard secrets or connection strings in committed files.

Verify /health and /v1/config over HTTPS; inspect logs for redaction and ingress behavior.
Android staging connectivity, APK CI, signed distribution and the full Phase 0 exit
are not provided by the backend-only workflow. API breaking-change comparison against
a release baseline will be added once the first contract has been reviewed.
