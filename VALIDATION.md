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
