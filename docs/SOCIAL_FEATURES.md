# Social features — source implementation

Added Android and API paths for all ten requested feature areas:

1. Profile gallery management, bio/interests editing, three distinct prompts and ideal weekend.
2. Replies to an identified profile prompt or photo; the server validates ownership and supplies the quoted context.
3. Explicit activity availability with a 1–24 hour expiry, separate from nearby location visibility.
4. Chat plans with up to three future times, duration, public-venue attestation, votes, unanimous confirmation and host cancellation.
5. Home with relevant nearby people, nearby pools, ticket commitments, chat plans and saved experiences.
6. Interest circles with rules, limited membership, discussions and host removal controls.
7. Saved events/pools, an optional weekly shortlist, and idempotent in-app digest delivery from the server's hourly worker.
8. Private post-plan choices; only reciprocal choices generate a meet-again notification. Choices can be withdrawn.
9. Authenticated memory previews, recorded uploader consent, private/group audience changes, owner/host removal and reporting.
10. Phone-verification labels, membership enforcement, block filtering, reporting, circle moderation and plan cancellation notices.

## Data and privacy

Migration: `202610180001_social_features`. Regenerate Prisma after applying the updated schema.
Existing profiles keep empty prompt/weekend defaults until edited; existing Hellos keep null context.
Plan participants are captured at proposal and narrowed to current eligible participants at confirmation.
New circle/group members do not gain access to earlier private chat plans.
Memory photos are compact JPEG previews (70 KB, up to 1280 px, at most 20 per person per plan), stored behind authenticated endpoints with no-store responses. Metadata is removed on the server. Profile galleries continue to use the existing Cloudinary upload flow. Consent is an uploader attestation, not automated verification of everyone pictured.
Public venues are entered and attested by the proposer; there is no venue-provider lookup.
The digest is in-app only and opt-in; this change does not send email or push notifications.
Activity status does not extend the existing 30-minute location-presence lifetime or turn location visibility on automatically.

## Verification status

At the user's request, final builds, test execution, migrations, emulator/adb use and Docker validation are deferred. Test source was added in `social-rules.test.ts` and `social.integration.test.ts`; it has not been run. The final Prisma client and OpenAPI artifacts must be regenerated during the later verification pass.
Earlier checks, before that instruction, found existing repository-wide lint failures and a Windows Gradle loopback startup error. Those checks do not verify this final implementation.

## Evaluation

Engagement effects remain hypotheses. Existing structured route/status logs and stored plan/Hello/circle states support counting profile edits, contextual Hellos, accepted Hellos, proposed/confirmed/cancelled plans, saved experiences and reciprocal reconnects. Compare outcomes and safety-report rates before asserting an engagement benefit; this implementation does not claim causality or improved retention.
