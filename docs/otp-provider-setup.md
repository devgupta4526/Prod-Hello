# OTP / photo setup — no real send or upload performed

1. Configure explicit sandbox E.164 numbers and six-digit codes in the ignored backend .env.
   Maximum 10 entries locally/staging, 3 production. No wildcard is accepted.
2. Set independent OTP_HASH_PEPPER, JWT_ACCESS_SECRET and JWT_REFRESH_PEPPER (32+ bytes).
3. Choose console for local/staging or msg91/twilio with complete credentials. Console
   is forbidden as either primary or fallback in production. Development console output
   includes OTP codes only deliberately outside production; never ship this provider live.
4. Set OTP_DAILY_SMS_BUDGET explicitly before any non-sandbox send. Default zero fails closed.
5. India: complete DLT registration and approved templates. MSG91 Flow fields in this
   adapter are OTP and APP_HASH; align the registered template or update the adapter.
6. Configure the 11-character SMS Retriever app hash for each signing variant using
   -Phello.smsAppHash=... and matching approved SMS template. Manual entry always remains.
7. Configure Cloudinary credentials server-side. Verify incoming image transformation,
   upload limits and EXIF removal against the chosen account before enabling public signup.

Remaining operational work: provider delivery receipts, signed webhook configuration,
global-budget alerting, dashboard delivery metrics, funded primary/fallback accounts,
production ingress proxy allow-list and real-device SMS Retriever checks.

No credentials belong in source control, Gradle resources, screenshots or test reports.
