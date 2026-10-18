# Feature backlog

Prepared from the Android client and backend source on 18 October 2026. This is a delivery checklist, not a claim that an API endpoint is a complete feature. **Partial** means implementation exists but the user journey is incomplete. **Verify** means evidence is insufficient to sign it off.

## P0 — resolve before a public release

| Feature | Current gap / evidence | Acceptance criteria |
|---|---|---|
| Accurate, consent-based nearby discovery | **Partial.** Discovery publishes fixed Delhi coordinates and OPEN availability on entry. Profile and pool creation also use fixed coordinates. | Use authorized coarse location; retain the user's availability; handle denied permission and stale location; never silently make an invisible user visible. |
| Real paid checkout | **Partial.** Backend creates a fabricated provider order; Android fabricates payment IDs/signatures and calls confirmation directly. | Real provider checkout, server verification, webhook reconciliation, cancellation and reservation expiry, duplicate-request protection, honest pending/failed states. Verify free bookings separately. |
| Scannable admission tickets | **Partial.** Dialog displays a static QR icon and raw payload. | Encode the actual authorized payload; scanning succeeds on a second device; validity and duplicate-entry handling are visible; readable booking reference as fallback. |
| Durable offline tickets | **Partial.** Ticket lists live in ViewModel memory; no durable ticket store was found in the reviewed client. | Previously downloaded valid tickets survive process death and airplane mode; make last-sync and invalidation limits clear. |
| Privacy-safe account changes | **Partial / verify.** This pass changes availability controls to explicit click handlers, avoiding writes during rendering/restoration. Pending/error rollback and privacy-safe location remain incomplete. | Initial rendering never writes presence. User changes show pending, confirmed or reverted state; sign-out removes account-specific local state. |
| Reliable Android feature tests | **Partial.** Existing local unit test checks addition; existing navigation test targets obsolete shell views. | Current navigation, consent, auth, payment boundaries, failure recovery, keyboard, rotation and accessibility tests pass. |
| Release configuration and deployment | **Verify.** Release defaults to an unconfigured URL. Earlier documentation does not establish a completed staging release. | Configured HTTPS staging, real-device end-to-end checks, signing, release build, environment validation and documented rollback. |

## P1 — complete the everyday experience

| Feature | Current gap / evidence | Acceptance criteria |
|---|---|---|
| Live chat and reconnection | **Partial.** Reviewed client fetches messages on entry and appends sent messages; no socket/poll reconnect client was found. | Incoming messages appear without reopening; reconnect and resume catch up without duplicates; read state is correct. |
| Durable message delivery | **Partial.** This UI pass preserves drafts and prevents simultaneous submits. It is not a durable outbox. | Pending/failed/sent states, explicit retry, idempotency, connectivity recovery and process-death behavior tested. |
| Notifications and deep links | **Partial.** Permission request exists; no Firebase messaging service/notification-channel implementation was found in the reviewed client. | Token lifecycle, opt-in controls, notification channels, correct destinations, signed-out routing and sensitive-content preferences. |
| Discovery controls | **Partial.** Current screen mainly presents a nearby list and availability controls. | Useful interest/distance filters, location-permission recovery, privacy-safe location labels and refresh; do not imply unavailable filters work. |
| Profile editing | **Partial.** Repository supports updating a profile; the current profile screen is primarily a read/control surface. | Edit name/bio/interests/photos with validation, save/cancel, upload progress and failure recovery. |
| Pool scheduling | **Partial.** Client chooses fixed location and relative start/end times. | User selects valid time/place/activity/capacity, previews the plan, receives confirmation and can manage it. |
| Search and discovery of events | **Partial.** Event feed exists; search/filter UI was not found in the reviewed screens. | Search, date/category/location filtering, clear free/paid prices, availability and sold-out states. |
| Pagination and refresh | **Verify.** List implementations do not establish a complete paginated refresh experience. | Large lists load incrementally, maintain scroll position, recover from load-more failure and avoid duplicate rows. |
| Account export delivery | **Partial.** Client requests an export; reviewed route creates a queued export record. | Background processing, status, secure download, expiry and retry work end to end. A request acknowledgement is not delivery. |
| Account-deletion completion | **Verify.** Request and cancel UI exist. | Scheduled purge, retention exceptions and revoked access are verified; the user sees accurate timing and completion. |
| Safety follow-through | **Partial / verify.** Block/report endpoints and UI exist. | Blocking immediately affects all relevant surfaces; report acknowledgement is durable; moderation/appeal and suspension flows are tested. |

## P2 — organizer and operations journeys

- [ ] Organizer application, review/status and onboarding UI. Backend routes exist; a complete Android organizer journey was not found.
- [ ] Event drafting, publishing, tiers, attendee management and cancellation UI.
- [ ] Scanner/check-in experience with camera permission recovery, distinct valid/duplicate/invalid results and manual lookup.
- [ ] Refund and cancelled-event communication, settlement/ledger visibility and reconciliation.
- [ ] Moderation/admin workspace and an auditable operations process.
- [ ] Operational monitoring, error reporting, performance baselines and release alerts verified in staging.

## Improvements implemented in the accompanying UI pass

These remain subject to the recorded build/device verification; they do not automatically close larger product features.

- Real HTTPS avatar loading through Coil, with initials for missing/error states and reduced-motion-aware crossfades.
- Shared visual styling, collapsing feed headers and adaptive card columns.
- Distinct empty/loading/failure rendering and retry for the main feeds.
- Semantic haptics with a persistent user preference and system-setting respect.
- Draft preservation during failed chat sends, send-in-flight protection and confirmed-send feedback.

## Delivery order

1. Consent/location and payment/ticket correctness.
2. Real-device UI verification and current feature regression tests.
3. Reliable chat, notifications, offline tickets and profile editing.
4. Pool/event discovery depth and organizer operations.

Backend ESLint had 177 errors in the preceding review. Treat that as engineering debt to resolve alongside the feature work, not as a feature or proof that all 177 issues affect runtime behavior.

## Verification status for the source-only delivery

Final code was not rebuilt or exercised after the user requested no more execution. Added device-test source is unexecuted. Do not mark the source changes as production-tested. See EXPERIENCE_DESIGN.md for the exact limitations.
