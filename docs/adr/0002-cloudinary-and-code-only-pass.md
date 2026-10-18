# ADR-002: Cloudinary and the code-only implementation pass

Date: 2026-10-17. User decision: Cloudinary for profile photos.

The user requested implementation through Phases 0–2, then explicitly instructed:
do not run the app; write code first and leave checks/tooling until the end.
Accordingly, new authentication/onboarding implementation is [~] source, not tested completion.
No SMS, Cloudinary upload, Android launch or hosted deployment is authorized by a claim
of completed testing. Earlier foundation tests do not validate later changes.

Cloudinary signatures are generated only by the backend. The client uploads directly
with a server-assigned public ID, signed format/transformation parameters and overwrite
disabled. Confirmation checks owner, expiry, provider resource identity, JPEG format,
dimensions and bytes. Client recompresses to JPEG with source EXIF omitted; server-signed
incoming transformation also strips profile metadata. API secret never enters the APK.

Required deployment settings: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY,
CLOUDINARY_API_SECRET. Missing configuration returns storage_unavailable; no fake upload.

Operational defaults introduced in source and requiring review: refresh lifetime 30 days;
SMS spending cap 0 until explicitly configured; twelve starter interest categories;
5 MiB maximum confirmed photo and one-hour upload confirmation window. These are
implementation choices, not additional OD defaults or confirmed product decisions.

Availability is a local preference at the Phase 2 boundary. Presence publication and
AT_EVENT ticket eligibility stay disabled until Phase 3/6; the UI explicitly states this.
This does not satisfy the blueprint's PUT /presence step, which remains a cross-phase gap.
