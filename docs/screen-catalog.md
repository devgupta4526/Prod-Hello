# Screen catalog — source implementation

Full end-to-end screen implementations in production Kotlin, Material 3 XML Views, ViewBinding, and Hilt.

| Screen / Flow | Source & Layout | Status |
| --- | --- | --- |
| **A01 Splash** | `AuthFragment` / `fragment_auth.xml` | Config check, minimum version check, session refresh routing, server waking state. |
| **A02 Welcome** | `WelcomeFragment` / `fragment_welcome.xml` | Good company intro, privacy statement, onboarding routing. |
| **A03 Phone** | `AuthFragment` / `fragment_auth.xml` | Locked +91, Indian E.164 phone validation, idempotency key generation. |
| **A04 OTP** | `AuthFragment` / `fragment_auth.xml` | Six visual cells, SMS Retriever integration, resend countdown timer, rate limit handling. |
| **A05–A06 DOB Gate** | `AuthFragment` / `fragment_auth.xml` | Date picker, adult-only backend check (BR-01), under-age blocking screen. |
| **A07 Profile Creation** | `AuthFragment` / `fragment_auth.xml` | Display name, bio, interest tags (3–8), Cloudinary signed photo upload with EXIF scrubbing. |
| **A08 Community Guidelines** | `AuthFragment` / `fragment_auth.xml` | Community ground rules, mutual consent agreement, server guidelines activation. |
| **A09–A10 Permissions** | `AuthFragment` / `fragment_auth.xml` | Coarse location permission, notification permission, graceful denial fallbacks. |
| **A11 Initial Availability** | `AuthFragment` / `fragment_auth.xml` | Status choice (Open to meet, Just browsing, Invisible). |
| **D01–D06 Discovery** | `DiscoveryFragment` / `fragment_discovery.xml` | Real presence updater (`PUT /v1/presence`), availability chips, nearby people cards with fuzzed markers & distance buckets, public profile bottom sheet (`bottom_sheet_user_profile.xml`), Say Hello modal (`dialog_say_hello.xml`), block & report moderation options. |
| **H01–H04 Inbox** | `InboxFragment` / `fragment_inbox.xml` | Received & Sent Hellos tabs, real-time Accept/Decline/Withdraw actions, mutual Connections tab, navigation to conversation. |
| **C01–C03 Chat** | `ChatFragment` / `fragment_chat.xml` | 1-on-1 and pool group messaging, `MessageBubbleView` bubbles with read receipts and timestamps, partner header with safety menu (Block user / Report user). |
| **P01–P07 Happening & Pools** | `HappeningFragment` / `fragment_happening.xml` | Active pools feed, "Host a Pool" modal (`dialog_create_pool.xml`), capacity constraint (2..12), pool details bottom sheet (`bottom_sheet_pool_details.xml`) with member list, pending knocks approval/rejection for hosts, leave/close actions, and group chat link. |
| **E01–E14 Events & Tickets** | `TicketsFragment` / `fragment_tickets.xml` | Explore Events feed with categories, tiers, dates, and fuzzed venues; booking bottom sheet (`bottom_sheet_event_details.xml`) with tier selection and `QuantityStepper` (1..6); My Tickets view with confirmed tickets and offline QR access modal (`dialog_ticket_qr.xml`). |
| **S01–S06 Profile & Safety** | `ProfileFragment` / `fragment_profile.xml` | Profile viewing, availability live toggle, active sessions list with per-device revocation, blocked users dialog (`dialog_blocked_users.xml`) with unblock action, GDPR data export trigger, and account deletion with 30-day grace period cancellation. |
| **Debug Gallery** | `debug/.../DesignGalleryActivity.kt` | Design system showcase: tokens, cards, avatars, message bubbles, stepper, and motion primitives. |
