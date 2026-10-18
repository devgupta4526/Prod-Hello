# Production readiness review — 17 October 2026

**Verdict: not ready for a public production release or real payments.** The workspace contains a built debug APK and substantial Android/backend implementation, but there are reproducible authorization defects and incompatible client/server contracts. Successful compilation does not establish working user journeys.

Scope: current workspace source, including unfinished changes from the ongoing implementation; existing build reports; backend type checking; selected tests; isolated service/handler probes. The existing APK was not launched or rebuilt. Its contents were not verified against every current source file. No production service, real payment, SMS, Cloudinary account or customer data was accessed.

## Release-blocking findings

### 1. Critical: paid tickets can be finalized without verifying payment

Location: [orders.service.ts](../backend/src/modules/orders/orders.service.ts:56), [orders.schemas.ts](../backend/src/modules/orders/orders.schemas.ts:33).

`confirmOrder` accepts a payment ID and signature but never verifies the signature, provider order ID or captured payment. It passes the stored order amount directly into `finalizeOrder`, so the amount check cannot establish that money was received. `createOrder` returns a fabricated provider order and `rzp_test_placeholder` for paid orders in every environment.

An isolated probe passed an invalid signature into the actual service with a fake repository; the service called finalization successfully. This establishes the missing service check, not a live exploit against a deployed database. The public route forwards the same validated non-empty strings to that service.

Required fix: disable paid-order mutations server-side until real provider integration exists. Persist a real provider order, verify the returned signature, fetch/verify payment capture, amount, currency and order binding, then finalize transactionally. A `purchasesEnabled: false` value in config does not protect the registered endpoints.

Razorpay explicitly requires callback signature validation: [Razorpay security checklist](https://razorpay.com/security/checklist/).

### 2. High: payment webhook verification is unsafe and can reject genuine events

Location: [orders.service.ts](../backend/src/modules/orders/orders.service.ts:128), [orders.routes.ts](../backend/src/modules/orders/orders.routes.ts:130).

The webhook secret falls back to a known literal when missing. Signature checking is skipped when NODE_ENV is test, independently of APP_ENV. The route reconstructs the body with JSON.stringify after parsing instead of retaining the exact received bytes. A legitimate signature can therefore fail when whitespace/encoding changes. Event processing trusts a notes field to identify the internal order and does not persist webhook deduplication records.

Required fix: validated mandatory provider configuration; raw-byte signature verification using constant-time comparison; no production bypass/default secret; provider order binding; durable event deduplication and idempotent processing.

### 3. High: Android and backend contracts do not agree

Locations: [EventsApi.kt](../app/src/main/java/com/example/romear/feature/events/data/EventsApi.kt:12), [EventsRepository.kt](../app/src/main/java/com/example/romear/feature/events/data/EventsRepository.kt:99), [ProfileApi.kt](../app/src/main/java/com/example/romear/feature/profile/data/ProfileApi.kt:14), [PoolsRepository.kt](../app/src/main/java/com/example/romear/feature/pools/data/PoolsRepository.kt:85).

| Flow | Android | Backend |
| --- | --- | --- |
| Event list | GET /v1/events, object containing items and tiers | GET /v1/events/nearby, array without tier detail |
| Create order | tierId and quantity only | requestId also required |
| Order response | totalPaise, expiresAt | amountPaise, reservedUntil |
| Confirm payment | /confirm-payment, paymentId | /confirm, razorpayPaymentId and razorpaySignature |
| Ticket list | items wrapper, nested event/tier, qrPayload | flat array, qrValue |
| Update profile | PUT /v1/me/profile | PATCH /v1/me/profile |
| Data export | GET /v1/me/export | POST /v1/me/data-export |
| Cancel deletion | /v1/me/cancel-delete | /v1/me/delete/cancel |
| Pool detail | host, members, activity, timeWindowStart | organizer, category, startsAt; membership list is separate |

The actual order schema rejected the exact shape emitted by Android in an isolated check. Other mismatches are direct route/parser comparisons. These lead to 404s, 400s and JSON parsing failures even though both projects compile.

Required fix: choose one documented contract, update both sides, and add client deserialization/request tests against actual server response fixtures. Complete real end-to-end journeys before counting these features as working.

### 4. High: discovery ignores user location and privacy preference

Location: [DiscoveryViewModel.kt](../app/src/main/java/com/example/romear/feature/discovery/ui/DiscoveryViewModel.kt:37).

Creating the discovery ViewModel automatically publishes OPEN presence at fixed New Delhi coordinates. Availability changes reuse those coordinates and report 10 m accuracy. The ViewModel does not read the onboarding OFF/BROWSING preference or check location permission. It can therefore publish presence after a user chose to stay invisible or denied location. Presence update and initial discovery loading also race independently.

Required fix: restore the actual saved choice; gate location-dependent work on permission; obtain a current coarse location; never substitute a real-looking fixed location; await presence success before discovery. Test OFF and permission denial across process recreation.

### 5. High: ordinary session access does not enforce completed onboarding

Locations: [app.ts](../backend/src/app.ts:189), [discovery.service.ts](../backend/src/modules/discovery/discovery.service.ts:58), [me.routes.ts](../backend/src/modules/me/me.routes.ts:238).

The session guard rejects suspended users but permits ONBOARDING and DELETION_PENDING users. A user with no DOB can set presence and query discovery because the viewer is not checked for completed onboarding, even though target users are filtered. The cancel-deletion handler unconditionally sets ACTIVE without requiring an existing deletion request or completed adult/profile/guidelines checks.

An isolated handler probe using an ONBOARDING identity caused an ACTIVE update. This does not bypass every target eligibility check, but proves that account status is not governed by a valid state transition.

Required fix: separate authenticated-self access from active-social access, enforce adult/profile/guidelines state on the server, and use conditional state transitions for deletion cancellation. Suspended users still need narrowly allowed self/logout/appeal routes.

### 6. High: chat authorization is incomplete

Locations: [chat.service.ts](../backend/src/modules/chat/chat.service.ts:76), [chat.repo.ts](../backend/src/modules/chat/chat.repo.ts:279).

Sending a message checks conversation membership but never checks the block relation. Blocking cancels pending Hellos but retains the connection, so a known conversation remains writable. Conversation detail can still reveal blocked participants. Delivery acknowledgment and mute handlers do not require membership; acknowledgment updates messages using the caller-supplied conversation ID.

Isolated probes showed a message write with zero block checks and an acknowledgment update for an outsider identity. No real user messages were created or changed.

Required fix: enforce membership, account eligibility and block policy on every conversation operation, including direct detail, receipts and mute. Apply relevant checks atomically with writes and add blocked/nonmember regression tests.

### 7. High: reservation lifecycle is not production-safe

Location: [orders.repo.ts](../backend/src/modules/orders/orders.repo.ts:156), [server.ts](../backend/src/server.ts).

`releaseExpiredReservations` exists but has no caller in the application source. Abandoned paid reservations can remain held. Finalization only short-circuits PAID, without rejecting EXPIRED/FAILED or synchronizing the state transition with expiry. The expiry function reads pending orders outside its mutation transaction and does not conditionally claim them. Concurrent expiry/finalization can produce failed transactions or inconsistent order state. Database inventory constraints help prevent overselling but do not replace correct order state transitions.

Required fix: scheduled durable expiry processing, locked/conditional per-order transitions, consistent tier locking, late-payment handling and real concurrent database tests. These races were identified statically, not exercised against a database in this review.

### 8. High: account deletion and export do not complete

Location: [me.routes.ts](../backend/src/modules/me/me.routes.ts:179).

Export only inserts a PENDING record. Deletion only sets DELETION_PENDING and removes presence. I found no account-erasure/export processor in the application source. Android cancellation/export routes also mismatch the backend. This is insufficient evidence of a working deletion or export flow; an externally managed process was not available for review.

Required fix: implement and verify completion, retries, retention exceptions, session/device cleanup and a usable deletion-request web page. Google Play requires in-app and web deletion request paths for apps supporting account creation: [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

## Scale and release evidence

- Discovery fetches all eligible active presences and their profiles/photos, calculates distance in application memory, then paginates. See [discovery.repo.ts](../backend/src/modules/discovery/discovery.repo.ts:67). This is not an indexed nearby query; load and latency must be measured after replacing it with database spatial filtering.
- Presence rate limits are stored in a process-local Map; they reset on restart, do not coordinate across instances and are not evicted.
- Release defaults to an unusable API hostname unless supplied a property. Only a debug APK and debug reports were found in outputs; release signing, minification behavior, staging connectivity and Play delivery were not verified.
- Android lint reports no issues, but several checks including LabelFor and HardcodedText are disabled. This is not accessibility or localization sign-off.
- The existing Android unit-test report contains only `addition_isCorrect` (2 + 2). It does not test application behavior.
- I did not assess rendered screen quality, startup/frame performance, ANRs, battery use, offline recovery, notification delivery, real devices, TalkBack or 200% font scaling. No app was launched.

## Checks performed

| Check | Result |
| --- | --- |
| Existing debug APK | Found, approximately 10.9 MB; not launched |
| Existing Android lint report | No issues reported under current disabled-check configuration |
| Existing Android unit report | 1 passing arithmetic example |
| Backend TypeScript `tsc --noEmit` | Passed |
| Backend env/auth-rules/business-rules tests | 24 passed across 3 files |
| Backend ESLint | Failed: 176 errors |
| Invalid payment signature service probe | Reached finalization — defect confirmed |
| Chat block-check probe | Message write reached with zero block checks — defect confirmed |
| Android order request vs server schema | Rejected — mismatch confirmed |
| Cancel deletion with onboarding identity | Requested ACTIVE update — defect confirmed |
| Outsider delivery acknowledgment probe | Reached message update — defect confirmed |
| Full database/provider/device/load suite | Not run in this review |

Probes invoked actual service/repository/handler code with in-memory substitutes for persistence. They demonstrate missing local checks, not measured deployed exploitability or real-device behavior. Existing passing tests do not cover these failures.

## Competitive assessment and next milestone

The codebase has useful foundations: structured Android architecture, encrypted credentials, server validation, OTP hashing, session rotation and database constraints. Those are necessary foundations, not evidence of market competitiveness.

The next milestone should be a reliable closed beta, not a public launch: fix payment and authorization defects; reconcile contracts; honor privacy/location preferences; finish deletion and reservation processing; prove complete journeys with real staging services. Then measure signup completion, discovery success, message delivery, booking correctness, crashes/ANRs, startup and scrolling on representative devices, plus retention in a focused local audience.

A social/events app also needs enough relevant nearby people and events, effective moderation and a clear reason to return. Code review alone cannot predict those outcomes or justify a numerical competitiveness score.
