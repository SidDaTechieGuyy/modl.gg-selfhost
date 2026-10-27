# Validation record

Validated locally on 26 October 2026 using Docker Desktop with Linux containers.
The original Java 21 / Spring Boot backend and React panel are retained.

- Backend: **950 tests passed**, no failures or skipped tests.
- Frontend: TypeScript, ESLint, production build, and **8 tests passed**.
- Both documented Docker test-image builds passed from a separate Git clone.
- Fresh clone: copied `.env.example`, generated a secret, then built and started
  exactly `mongodb`, `backend`, and `frontend`; all became healthy.
- Empty-volume setup: two concurrent owner registrations yielded exactly one
  creation and one conflict. MongoDB contained exactly one BCrypt-hashed owner.
- API/browser: first-boot setup renders, registration provisions the existing
  defaults, owner login reaches the original dashboard, logout revokes sessions,
  and both username and email logins work. Registration closes afterwards.
- HTTP and realtime: explicit origins reject untrusted origins; `*` and `dynamic`
  reflect incoming origins with credentials. Each setting passed a live binary
  WebSocket client/server hello exchange through nginx. Unit tests additionally
  cover comma-separated lists, malformed origins, and origin normalization.
- Uploads: live presign/PUT/confirm/download passed. Invalid download signatures
  and rejected file types returned appropriate errors. Unit tests cover expired
  signatures, traversal, symlinks, restart persistence, length/type mismatches,
  and overwrite protection.
- Staff: creating and accepting a private invitation with a password produced an
  independent staff session. Staff sharing generated a working ticket capability;
  the ticket ID alone was denied. Raw chat remains private to the staff panel.
- Recovery: stopped application writes, dumped the whole database and copied the
  upload tree, replaced both disposable volumes, restored the archive and files,
  fixed ownership, then restarted. Owner login and all three original uploaded
  files were verified after restore.
- Source/configuration/dependency scan found no removed service or feature
  references in retained text files. Shared upstream protocol libraries still
  contain unused wire fields; those external schemas are not vendored here.

The installation targets a fresh instance; no hosted-data migration was attempted.
Public certificate issuance, the external AI provider, and a real Minecraft plugin
were not exercised: they require the operator's domain, provider key, or plugin.
Review the private player-link integration note in `SELF_HOSTING.md` before
connecting an existing plugin. Builds still require the upstream shared-package
registry. See `UPSTREAM.md` for exact source commits and licensing.

Disposable test accounts and volumes are removed after validation. The delivered
local app starts without an owner so its first visitor sees registration.

## Usage page and replay follow-up

Restored the upstream usage page's storage overview, AI card, replay-retention
controls, file table, search, filtering, sorting, pagination, downloads and deletion.
Commercial controls and limits remain absent. Storage reflects local file usage;
the AI card explicitly reports unavailable tracking when the backend supplies no
request counters. Automatic optional ticket email fields were removed, and
remaining required contact-email fields no longer promise notification delivery.
Empty optional emails are omitted from requests.

A synthetic replay generated with the backend's exact replay writer passed the
plugin upload/confirm flow, public metadata lookup, byte-for-byte local download,
HTTP range download, storage listing and retention lookup on a separate disposable
instance. The browser viewer loaded the block snapshot and played its one-second
timeline. This extends the earlier validation; a real Minecraft server/plugin
installation has still not been exercised.

The follow-up replay/replay-lite/retention regression subset passed as well, along
with the frontend production build, lint/type checks, eight frontend tests, and
the expanded disposable acceptance test including email-free support creation.
The isolated replay-check instance was removed after verification; operator data
was not changed.

## Usage page functional checks

The AI usage card and its frontend tracking data were removed at the operator's
request. This does not disable AI moderation. The remaining page uses a two-card
layout for local storage and replay retention.

A separate disposable instance verified upload/list/download, correct storage
byte totals, single and bulk deletion, rejection of another instance's file keys,
persisted retention changes and enable/disable, invalid retention values, and
stale-version conflict handling. Twelve synthetic files were used for browser
checks: search reduced the table to one matching file, the ticket filter showed
four ticket files, ascending size sort produced 1 through 10 bytes on page one,
and pagination showed the remaining 11- and 12-byte files on page two. The browser
retention Save button returned success and displayed the new value. No operator
files or settings were changed.

The frontend now checks the returned deletion count before reporting success and
prunes stale file selections after refreshing the file list. Production build,
TypeScript, ESLint, and all eight frontend tests passed.
# Factory reset follow-up

Username-only accounts follow-up: registration and staff invitations no longer
collect email addresses; login uses case-insensitive usernames. The disposable
acceptance suite passed registration without email, rejected email login, reserved
owner names, staff invitation/activation, independent staff permissions, owner
rename persistence and session revocation. All 958 backend and 12 frontend tests
passed. Existing internal account references are retained for compatibility;
operator passwords and stored data were not reset.

Registration typography follow-up: restored the landing registration theme and
Inter/Audiowide fonts (served locally with their licenses). Frontend schema tests
verify matching confirmation and the eight-character minimum, with 11 frontend
tests passing. All 956 backend tests passed. The full self-hosting acceptance suite
passed on a disposable `modlregistrationcheck` project using an exactly
eight-character password for owner and staff; a seven-character registration was
rejected. Browser inspection confirmed the original 448px form width and font
families. Owner credentials/data in the main instance were not changed by testing.

The reset implementation passed all 956 backend tests, the 8 frontend tests, and
the TypeScript/ESLint/production frontend build. On a separate `modlresetcheck`
Compose project, the reset acceptance suite verified rejected anonymous and
incorrect-confirmation requests, backend restart, deleted uploads/settings/owner
credentials/sessions, and successful registration of a replacement owner. Browser
checks verified the warning, disabled final button until phrase/password entry,
and cancellation. The operator's main database and uploads were not reset.
