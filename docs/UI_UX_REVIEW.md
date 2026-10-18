# UI and code review — 18 October 2026

## Verdict
The app has a coherent warm palette and native Material foundation, but it is not ready for a premium-quality or production-ready sign-off. This review inspected Android resources, navigation, discovery, shared components, chat, tickets and selected backend code. It is not an exhaustive security audit or a completed visual/device audit.

## High-priority unresolved findings

1. **Profile photos never load.** `AvatarView.load()` ignores `url` and calls only `bind()`. Discovery, chat and profile cards therefore show generic icons. Add a lifecycle-aware image loader with caching, error states and recycling cancellation.
2. **Presence is inaccurate and can override privacy choices.** `DiscoveryViewModel` publishes OPEN at fixed Delhi coordinates on initialization; availability changes in discovery/profile and pool creation also use hardcoded coordinates. Use permission-aware location and persisted availability, and never publish presence merely because a screen opens.
3. **Paid checkout is incomplete.** `OrdersService.createOrder()` returns a placeholder Razorpay key and fabricated provider order ID. Production paid booking needs a real provider integration and verified settlement before release.
4. **Ticket QR is not a QR image.** `TicketsFragment.showTicketQrDialog()` assigns the payload to a text label while the image remains a static QR icon. Render and validate a scannable code, plus a readable fallback and clear offline behavior.
5. **Android tests do not establish UX correctness.** The local unit test only checks addition. `ShellNavigationTest` references old shell IDs instead of the current feature screens. Update navigation coverage and add keyboard, font-scale, rotation and error-state checks.

6. **Chat can lose a draft on network failure.** `ChatFragment` clears the composer immediately when sending, while `ChatViewModel` only emits an error if the request fails. Preserve pending messages or restore the draft and offer retry.

## Improvements made in this pass

- Discovery identity, status and action no longer compete in one narrow horizontal row. A barrier keeps interests below the full identity block; the action has its own row and at least a 48dp target.
- Discovery header uses clearer hierarchy and removes the unverified Delhi label and decorative emoji from availability controls. Availability always has a selected chip.
- Loading, results, empty and load-failure states are collected together. A failed request has a persistent retry action instead of masquerading as an empty neighborhood.
- Decorative empty-state icons in discovery, happening, inbox and tickets are excluded from screen-reader focus.
- Chat controls have at least 48dp targets. System Back from chat can return through the navigation stack rather than jumping directly to discovery.
- Main window now accounts for keyboard and bottom system insets when navigation is hidden.
- Primary action color has 5.33:1 contrast against white. Dark mode has explicit primary-container, subtle-outline and status palette overrides.
- Added width-qualified gutters for screens at least 600dp/840dp wide and shorter illustrations in qualifying landscape layouts. These are initial adaptive improvements, not proof of tablet optimization across every screen.

## Remaining design work

Establish a unified screen-header, spacing, card, empty-state and typography system across every feature; replace remaining hardcoded text and ad hoc status styling; audit long names and large font sizes; design meaningful offline/retry states across all feeds; verify list performance and image loading; add appropriate tablet navigation and content widths; validate screen-reader order and focus. Avoid fake people or fabricated activity to make empty screens look populated.

## Verification

- Backend TypeScript type checking and production compilation passed.
- Backend ESLint failed: 177 errors, including unsafe types, deprecated schema methods and unused code. No backend source was changed in this pass.
- Selected backend tests: 24 passed across env, auth rules and business rules. Database integration tests were not run.
- Final Android verification passed: `:app:assembleDebug`, `:app:lintDebug`, and `:app:testDebugUnitTest`. The unit suite contains only the existing addition example, so its passing result does not validate feature behavior. All resource XML also parsed successfully.
- Emulator startup failed to expose Android package/activity services. No rendered screen or real-device visual sign-off was obtained. The emulator started for this review was stopped.
- Required visual matrix: compact phone, standard phone, tablet, landscape; light/dark; normal and 200% font; keyboard open; offline/empty/loading/error/populated states. Test actual photos, long names and scannable tickets.

## Follow-up source pass

See FEATURE_BACKLOG.md and EXPERIENCE_DESIGN.md for the subsequent implementation and current status. Avatar loading and draft preservation have now been implemented in source, together with shared styling, semantic haptics and adaptive layouts. These supersede the corresponding source findings above, but have not been verified end to end. Earlier passing checks in this document describe the earlier review snapshot, not the latest changes. Final execution was stopped at the user's request.
