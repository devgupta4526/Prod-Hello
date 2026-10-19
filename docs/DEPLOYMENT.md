# Public test API

API base URL: https://prod-hello-api.onrender.com/

Render service: https://dashboard.render.com/web/srv-dbafo5brjlhs73a3kq00

Neon project: `Prod-Hello` (`little-firefly-00342307`), Singapore, PostgreSQL 17.
Render runs a free Node web service in Singapore. Neon belongs to the free `Dev`
organization. Secrets and database connection strings are stored in Render.

The service was created directly through Render, rather than as a Blueprint.
Its settings are managed in Render; `render.yaml` remains a template and is not
automatically synced to this service. Build and start commands include `cd backend`
because the service's root directory is the repository root.

```powershell
Invoke-RestMethod https://prod-hello-api.onrender.com/health
Invoke-RestMethod https://prod-hello-api.onrender.com/v1/config
```

For Android staging:

```powershell
.\gradlew.bat assembleStaging -Phello.stagingUrl=https://prod-hello-api.onrender.com/
```

The free Render service can sleep while idle; allow time for the first request.
Database migrations run before the API starts. Real SMS is disabled, and sandbox
login requires an explicitly configured test number in `OTP_SANDBOX_NUMBERS`.
Photo uploads require Cloudinary credentials. Browser clients need their HTTPS
origin configured as `ADMIN_ORIGIN`; native apps and curl are not subject to CORS.

The API contract is in `docs/openapi.json`. Protected routes require an access token.

Verification completed: `/health` and `/v1/config` return 200; `/v1/me` without a
token returns 401. Sandbox OTP request (202), OTP verification (200), authenticated
profile (200), and logout (200) were tested against the public HTTPS API. The
test account is in onboarding. The sandbox phone and code are configured only in
Render and are intentionally omitted from this repository.

## Demo data

The hosted staging database contains five fictional demo profiles with labeled
avatar images, four nearby presences, three pools with group conversations,
and two published events with four ticket tiers around Nagpur. Use your
existing sandbox login. Demo accounts have synthetic `+999` identifiers and
cannot sign in through phone verification; no SMS is sent by seeding.

Set the device/emulator location near Nagpur (21.1458, 79.0882) and enable
discovery to test nearby people. Demo presence expires after 24 hours.
Events start four and six days after the initial seed; pools start one to three
days after it. Existing user accounts and activities are preserved.

To rerun against a deliberately selected local or staging database, set its
`DATABASE_URL` and `APP_ENV` in the ignored `backend/.env`, then run
`npm run seed` from `backend`. Optional `SEED_AVATAR_BASE` specifies the URL
directory containing `avatar-0.jpg` through `avatar-4.jpg`; without it the seed
uses a generic Cloudinary sample image. The hosted seed uses labeled avatars
under `https://res.cloudinary.com/zuervxin/image/upload/hello/demo`.

The seed uses deterministic IDs and a transaction. Rerunning creates missing
demo records and refreshes demo presence, demo locations, and future demo dates;
it preserves existing profiles, memberships, ticket inventory, and user edits. It rejects APP_ENV
values other than `local` and `staging`. No payments or bookings are created.
