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
