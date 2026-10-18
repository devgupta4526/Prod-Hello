# Architecture

Android uses Kotlin, XML Views, Hilt, ViewBinding and Navigation. Fragments render
ViewModel state. Repositories perform Retrofit/OkHttp work on an IO dispatcher.
TokenStore encrypts credentials with Android Keystore AES-GCM; backup excludes them.
The authenticator serializes refreshes and retries once. SavedStateHandle retains
onboarding form state. Server-side gates remain authoritative.

Backend uses strict TypeScript, Fastify, Zod and Prisma/PostgreSQL. Routes call services
and repositories. Routes declare public/session/onboarding access. Onboarding mutations
require adult verification except DOB itself. Self responses contain completion flags.
OTP uses HMAC hashes, durable idempotency, atomic budgets, one-time consumption and
committed failed-attempt counts. Sandbox numbers use the same verification pipeline.
Configured SMS providers support failover. Twilio callbacks verify provider signatures;
MSG91 callbacks remain pending provider setup. Refresh replay and logout revoke families.
Rotation and revocation share a database lock. Previously saved DOB cannot be replaced.

Cloudinary uploads are signed for an owned pending upload. Android removes EXIF and
resizes JPEGs; the signed transformation strips metadata too. The backend verifies
format/dimensions/size before confirming. Secrets never reach Android. Signing has an
hourly budget; confirmation expires after an hour and limits profiles to six photos.

Rate limits persist in PostgreSQL. Proxy trust stays disabled until ingress verification.
Logs contain request IDs, methods, registered routes and status only. Social features
remain disabled. External provider credentials/deployments are not configured here.
See PROGRESS.md for outstanding implementation and verification gates.
