# Data model — Phase 0 foundation

[~] User, Profile, Session and Block follow blueprint §8. All identifiers are UUID fields;
future application creation uses UUIDv7. Database identity defaults to ONBOARDING/USER,
never ACTIVE. Phone uniqueness supports BR-02 but OTP identity enforcement is not built.
Profile keeps age display off and discovery radius 5 km by default (OD-7, BR-12).

Session stores refresh token hashes, rotation-family IDs and replacement references;
token issuance, rotation and reuse detection are Phase 2. Block stores a unique directed
pair with a reverse index; mutual exclusion on every surface is Phase 3 onward, not done.
Foreign keys restrict accidental deletion; account anonymization is a later safety slice.
Timestamps use timestamptz(3); DOB uses date. Constraints enforce profile bounds and no
self-blocking. Indexes support foreign keys, session-family revocation and expiry.

RateLimitBucket is infrastructure-only: keyed HMAC of connection IP and route, minute
window, capped count. Its composite primary key supports atomic upserts across processes;
the window index supports hourly retention cleanup. It contains no plaintext IP.

Migrations are forward-only. Testcontainers uses disposable databases. Hosted Neon branch
validation remains required before staging deployment. No user database has been altered.
