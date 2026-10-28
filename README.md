# modl.gg — self-hosted instance

The existing modl panel and Java backend, adapted for one operator on one domain.
First-boot registration reuses the upstream landing page; accounts use usernames
and passwords. This repository contains two application folders:

- **frontend**: the original React panel, adapted registration page, and nginx gateway.
- **backend**: the original Java 21 / Spring Boot backend and its tests.

Docker Compose runs exactly three services: **mongodb**, **frontend**, and **backend**.
All installation, operation, testing, validation and provenance notes are collected here.

- [Prerequisites](#prerequisites)
- [Install and configure](#install-and-configure)
- [Deploy with Coolify](#deploy-with-coolify)
- [GitHub builds and image deployment](#github-builds-and-image-deployment)
- [First setup and staff](#first-setup-and-staff)
- [Minecraft plugin connection](#minecraft-plugin-connection)
- [HTTPS with Caddy](#https-with-caddy) or [nginx](#https-with-nginx)
- [Persistence and backups](#persistence-and-backups)
- [Troubleshooting](#troubleshooting)
- [Factory reset](#factory-reset)
- [Player ticket links](#player-ticket-links)
- [Testing](#testing)
- [Validation results](#validation-results)
- [Source provenance and licensing](#source-provenance-and-licensing)

## Prerequisites

Use a Linux server with Docker Engine and the Docker Compose plugin, or Docker
Desktop with Linux containers on Windows/macOS. Allow at least 4 GiB RAM and enough
disk space for uploaded evidence and database backups. Java and Node are supplied
by the build containers; they need not be installed on the host. Initial builds
require internet access to the image, dependency, and upstream modl registries.

For public access, point a domain at your server and install Caddy or nginx on the
host. Keep ports 80 and 443 available to that proxy. MongoDB and the backend do not
publish host ports. Plain `docker compose up` automatically loads the local
`docker-compose.override.yml`, exposing the gateway only on `127.0.0.1:8080`.
The base `docker-compose.yml` publishes no host ports for container-based proxies.

## Install and configure

Clone **this converted repository**, then enter its root:

```sh
git clone https://github.com/SidDaTechieGuyy/modl.gg-selfhost.git modl-selfhost
cd modl-selfhost
cp .env.example .env
```

Edit `.env` for your public address. No signing secret needs to be entered:

| Variable | Meaning |
| --- | --- |
| `BASE_URL` | Public address including its scheme and optional port, with no path or trailing slash. Use `http://localhost:8080` locally or your `https://…` address publicly. |
| `MONGODB_URI` | Database connection; the supplied Compose address works without modification. |
| `GEMINI_API_KEY` | Optional. Needed only if you enable the external AI moderation provider. |

For example, a public instance can use:

```dotenv
BASE_URL=https://moderation.example.org
```

HTTP, request validation and browser WebSockets automatically trust the origin in
`BASE_URL`, including its scheme, hostname and port. Other browser origins and
opaque origins such as `null` are rejected. There is no separate origin setting.
Native plugin WebSockets may omit Origin but still require their API key.
HTTPS sessions use Secure, HTTP-only, SameSite=None cookies; local HTTP sessions
use SameSite=Lax.

The backend generates a cryptographically random signing key on first boot and
stores it at `/app/uploads/.instance-secret` with owner-only permissions. Keep
this hidden file in uploads backups. It survives restarts and factory resets.
An existing `SESSION_SECRET` environment value is imported on the first upgrade
so existing file and ticket links keep working. Once that upgrade starts
successfully, the old environment value can be removed. An override that differs
from the persisted key is rejected rather than silently invalidating links.

```sh
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 backend
```

All three services should become healthy. Locally, visit http://localhost:8080.
For a public instance, configure HTTPS below before creating the owner account.

## Deploy with Coolify

For a Git-based Coolify application, use `/docker-compose.yml` as Docker Compose
Location. Load/reload the Compose definition from the repository after pulling
updates; changes belong in Git rather than the rendered Compose preview.
Do not include the local `docker-compose.override.yml` in Coolify's file list.

Assign your HTTPS domain to the **frontend** service and set its internal target
port to **80**. Set `BASE_URL` to that public address, for example
`https://moderation.example.org`, with no internal port. Then save and redeploy.
MongoDB and backend do not need domains or published ports. The Coolify proxy must
share a Docker network with frontend; use Coolify-managed routing/network settings.

The base Compose file uses `expose: ["80"]` rather than a host-port mapping, so it
does not compete for host port 8080 (which may already be in use by Coolify or
another application). Changing the host mapping to 8181 is unnecessary for proxy
routing. The public URL still uses standard HTTPS port 443.

For a manually managed host reverse proxy or localhost testing, use plain
`docker compose up -d` to include the local override. If you specify `-f` yourself,
include both files when you want the localhost mapping:

```sh
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d
```

See [Coolify's Compose guide](https://coolify.io/docs/applications/builds/docker-compose)
for repository-managed configuration and per-service domain routing.

## GitHub builds and image deployment

`.github/workflows/images.yml` runs on pushes to `main` and manual runs from
GitHub's Actions tab. It uses GitHub-hosted Linux runners; no runner installation
or build tools are needed on your home server. Both Docker test targets must pass
before publication. Frontend tests also run the TypeScript check, lint and production
build. Browser/Compose end-to-end tests are not run by this workflow.

The workflow publishes `linux/amd64` images (suitable for Intel/AMD servers):

- `ghcr.io/siddatechieguyy/modl-backend:latest`
- `ghcr.io/siddatechieguyy/modl-frontend:latest`

Each image also has a full Git commit SHA tag. Both commit-tagged images are
published before the workflow updates `latest` and optionally calls Coolify.
Updating two registry tags is not atomic: if promotion fails, do not deploy
`latest`; use the matching commit tags or rerun the workflow successfully.
Docker layer caching reduces repeated builds. Gradle cache mounts may still need
to download dependencies on fresh runners. Forks publish under their owner's
lowercase namespace; set `MODL_IMAGE_OWNER` in Compose accordingly.

### First publication

1. Open the GitHub repository's **Actions** tab and enable workflows if prompted.
2. Open **Test and publish Docker images**, select **Run workflow**, and use `main`.
   A push to `main` also starts it. Publication uses GitHub's temporary
   `GITHUB_TOKEN` with package-write permission; no personal token is needed in CI.
3. Wait for the entire job to succeed. Its summary contains the published commit.
4. Under your GitHub profile's **Packages**, open each image's package settings.
   Make both packages public for anonymous server pulls. Newly created packages
   may be private even when the source repository is public. For private images,
   configure a GHCR credential with `read:packages` in Coolify's registry settings.

### Switch the existing Coolify application

Back up MongoDB and uploads first. Update the existing application in place;
retain its resource identity, volume names and environment values. Creating a new
application can create new empty volumes instead of using your existing data.

1. Disable Coolify's Git-push automatic deployment; CI should finish publishing first.
2. Change **Docker Compose Location** to `/docker-compose.images.yml`, then reload
   the Compose definition. This file contains exactly the same three services,
   named volumes and network as the source-build file, with images instead of builds.
   Do not include the local `docker-compose.override.yml` in Coolify.
3. Keep the public HTTPS domain assigned to `frontend`, internal port `80`, and
   keep `BASE_URL` set to that public address. Preserve any existing `SESSION_SECRET`
   override and all other configured environment values.
4. Set `MODL_IMAGE_TAG` to `latest`, or use the full successful commit SHA for an
   explicitly pinned deployment. Both services use the same value.
5. Deploy with image pulling enabled. Confirm logs show image pulls rather than
   Java/Node compilation, and verify login, uploads and the existing data.

For a plain Docker host, the equivalent command is:

```sh
docker compose -f docker-compose.images.yml pull
docker compose -f docker-compose.images.yml up -d
```

This image Compose file publishes no host ports; it expects a container-based
reverse proxy such as Coolify's. Retain the same Compose project name when
switching an existing plain Compose installation to preserve its volumes.

### Optional automatic deployment after publication

First complete one manual deployment. Then enable Coolify API access if needed,
create a deploy-only API token under **Keys & Tokens > API Tokens**, and copy the
application's **Webhooks > Deploy Webhook (auth required)** URL. In GitHub,
**Settings > Secrets and variables > Actions**, add repository secrets:

- `COOLIFY_DEPLOY_WEBHOOK`: the copied HTTPS URL.
- `COOLIFY_TOKEN`: the deploy-only token.

The Coolify API URL must be reachable from GitHub's runners. Leave both secrets
unset for manual deployment. Do not commit either value. The webhook is called
only after tests and both image publications/promotions succeed. It queues a
deployment; a successful webhook response does not prove the app is healthy.
Check Coolify's deployment logs. For automatic rolling updates use
`MODL_IMAGE_TAG=latest`; a pinned SHA intentionally stays pinned until you change it.

To roll back, set `MODL_IMAGE_TAG` to a previous successful commit SHA and redeploy.
Image rollback does not undo database changes; restore backups if required.

References: [GitHub image publication](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images),
[Coolify deploy webhooks](https://coolify.io/docs/core/automation/deploy-webhooks).

## First setup and staff

The first visit displays the registration page. Supply a server name, owner
username and password (entered twice). Passwords require at least 8 characters and at most
72 UTF-8 bytes, and are stored as salted BCrypt hashes. No email address is required.

Registration initializes the existing roles, settings, forms, and default content,
then establishes a session and opens `/panel`. Log out and use `/auth` to log back
in with the owner username and password. Registration returns a conflict
after the owner exists, and `/register` redirects to login. Concurrent registration
requests are serialized by MongoDB's unique owner identifier. If setup is
interrupted after the owner is saved, log in again to retry initialization.

Invite staff from settings, then use **Share invitation link** in the pending
invitation's menu. Share that link privately. The recipient sets their own password
and logs in using the invited username. Links expire after 24 hours. Existing role
permissions still apply. Changing the username in profile settings also changes
the login username. Usernames are case-insensitive and use 3–32 letters, numbers,
underscores or hyphens. Existing accounts retain their passwords, permissions and
sessions; use their existing username to log in. Email login is disabled. Some
upstream database/protocol fields retain their legacy `email` names as stable
internal account references; new accounts store usernames in those fields.

Set the Minecraft plugin's panel address to the same `BASE_URL`, and obtain its
API key from the panel's server settings. Native `/v1/…`, `/v2/…` and `/v3/…` routes and browser
`/api/v1/…` routes both reach the backend. Realtime uses the same domain at
`/api/v1/realtime/ws`, with its scheme derived from `BASE_URL`.

New player tickets receive an access capability in their browser. Keep the
original ticket link/browser data; a ticket identifier alone does not grant access.

## Minecraft plugin connection

Checked upstream [modl-gg/minecraft](https://github.com/modl-gg/minecraft) at
commit `dffdf727875bcca11ae62fb26b742d1c3d6533e4`. You do not need to edit or rebuild
the plugin to change its backend host.

1. Complete registration in this web app and copy its API key from **Settings →
   General → Server Config**.
2. Put that key in the plugin's data-directory `boot.yml` under `api-key`.
   Choose `mode: standalone` for a single Paper/Spigot server, or use the plugin's
   proxy/bridge deployment modes as appropriate. Leave `testing-api: false`.
3. Set the **Minecraft server process** environment variable to your public origin:

   `MODL_API_URL=https://moderation.example.org`

   Alternatively, put the JVM option **before** `-jar` in its startup command:

   `java -Dmodl.api.url=https://moderation.example.org -jar paper.jar nogui`

4. Restart the Minecraft server. Set this on the proxy/core process when using
   proxy mode. Bridge-only servers connect to that core using their bridge settings.

Use the origin only: do not append `/api`, `/v1`, `/v2` or `/v3`. The JVM
property takes precedence over the environment variable. These are plugin settings,
not additional variables needed by this application's Compose stack. A localhost
address only works when it is reachable from the Minecraft process; a different
machine/container must use your reachable public HTTPS address.

The plugin's [BackendHost](https://github.com/modl-gg/minecraft/blob/dffdf727875bcca11ae62fb26b742d1c3d6533e4/core/src/main/java/gg/modl/minecraft/core/boot/BackendHost.java)
resolves that override. Startup calls `/v2/minecraft/startup` with `X-API-Key`;
normal requests use native `/v1` or `/v3` paths. Startup returns the panel and
WebSocket URLs derived from this app's `BASE_URL`. The plugin uses those URLs
and authenticates WebSockets with its API key without a browser Origin header.
All routes pass through the same HTTPS gateway; no separate public backend or
WebSocket port is required. The gateway includes the native v2 startup route.
The plugin's in-console SaaS registration wizard should not be used: register in
the browser first, then configure the existing instance's key.

This is source inspection and API-level validation, not a live Minecraft-server
test. Some upstream player-ticket links are still built from IDs; follow the
[Player ticket links](#player-ticket-links) compatibility note.

## HTTPS with Caddy

Run Caddy on the host with this Caddyfile, replacing the example hostname:

```caddyfile
moderation.example.org {
    reverse_proxy 127.0.0.1:8080
}
```

Caddy handles certificates and WebSocket upgrades. Set `BASE_URL` to `https://moderation.example.org`, then run:

```sh
docker compose up -d
```

Keep the gateway bound to loopback. If the proxy runs in another container, connect
it to the app network and proxy to `frontend:80` instead of its own loopback.

## HTTPS with nginx

Obtain a certificate for your hostname, then use a host nginx server block like:

```nginx
map $http_upgrade $modl_connection {
    default upgrade;
    '' close;
}
server {
    listen 443 ssl;
    server_name moderation.example.org;
    ssl_certificate /etc/letsencrypt/live/moderation.example.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/moderation.example.org/privkey.pem;
    client_max_body_size 1024m;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $modl_connection;
        proxy_read_timeout 120s;
    }
}
server {
    listen 80;
    server_name moderation.example.org;
    return 301 https://$host$request_uri;
}
```

Place `map` in nginx's `http` context. Reload nginx after validating its configuration.

## Persistence and backups

Compose declares `mongodb_data` for `/data/db` and `uploads_data` for `/app/uploads`.
Their actual names have the Compose project prefix. Ordinary restarts, image
rebuilds, and `docker compose down` preserve them. `down --volumes` deletes them.
The existing application stores global records in `modl` and operational records
in `modl_instance`; replay training also uses `training_data`. Back up the whole MongoDB instance, not just the database name
at the end of the URI. Include upload sidecar metadata, the hidden `.instance-secret` signing key, and your `.env` configuration.

For a consistent maintenance backup, stop application writes while MongoDB remains
running. These commands are for a POSIX shell:

```sh
mkdir -p backups
docker compose stop frontend backend
docker compose exec -T mongodb mongodump --archive --gzip > backups/mongodb.archive.gz
docker compose cp backend:/app/uploads backups/uploads
# Securely copy .env to your private backup location as well.
docker compose start backend frontend
```

Store a dated copy of each backup elsewhere. Do not commit `.env`, database dumps,
or private evidence. Test restoration before relying on backups.

To restore into a new installation, restore `.env` first, then:

```sh
docker compose up -d mongodb
docker compose create backend
docker compose exec -T mongodb mongorestore --archive --gzip --drop < backups/mongodb.archive.gz
docker compose cp backups/uploads/. backend:/app/uploads
docker compose run --rm --no-deps --user root --entrypoint sh backend -c 'chown -R 1001:1001 /app/uploads'
docker compose up -d
```

Restore the original hidden `.instance-secret` file; replacing it invalidates
existing signed file and ticket links. For backups taken before automatic key
generation was added, retain the original `SESSION_SECRET` for the first startup. This conversion targets a fresh instance. Importing an existing
hosted database or its external files requires a separate data migration.

## Troubleshooting

- **Docker cannot connect:** start Docker Desktop/Engine. On Windows, verify WSL
  and virtualization are installed and any required restart has completed.
- **Build cannot download a modl package:** check DNS and connectivity to
  `nexus.modl.gg`. The shared upstream packages are still build dependencies.
- **Backend unhealthy:** inspect `docker compose logs backend` and MongoDB health.
  Check uploads-volume permissions and that the database address resolves
  from inside the container.
- **Registration says it is closed:** the owner record already exists. Log in;
  redeploying containers intentionally does not reset ownership.
- **Login or uploads fail after enabling HTTPS:** make `BASE_URL` match the actual
  public scheme, host, and port. Recreate the backend after editing `.env`.
- **Origin rejected:** make `BASE_URL` match the browser origin exactly, including
  scheme and port. Apply changes with `docker compose up -d`.
- **Realtime disconnects:** preserve proxy upgrade headers and a read timeout
  above the heartbeat interval. The WebSocket address uses the same public domain.
- **Upload fails:** check disk space, size limits, and volume ownership (UID 1001).
  Expired upload links must be requested again. Uploads publish only after their
  full declared byte count has been received.
- **AI unavailable:** ordinary moderation works without a provider key. Configure
  the optional key before enabling AI review.

## Factory reset

The owner can use **Settings → General → Server configuration → Factory reset**.
Back up the database and uploads before proceeding. The action requires a warning
confirmation, typing `RESET EVERYTHING` exactly, and entering the current owner
password. Other staff cannot authorize a reset, even if they can modify settings.

Reset permanently removes all application records from `modl`, `modl_instance`,
and `training_data`, including accounts, sessions, API keys, settings, moderation
history, tickets, audit logs, and replays. It also clears the uploads volume,
including file metadata. The persistent signing key, collection indexes, Docker
configuration, `.env`, and external backups remain. Minecraft plugins must use the new API key after setup.

The backend writes a durable request into the uploads volume, pauses new requests,
then exits. Compose's `restart: unless-stopped` restarts it; cleanup runs during
startup before serving requests, and the page waits for registration to reopen.
If cleanup fails, startup fails and retains the request marker so the next start
retries cleanup. Check `docker compose logs backend` and fix database or upload
permissions rather than deleting the marker. Outside Compose, run the backend
under a process supervisor that restarts it after exit.

## Player ticket links

To obtain a player-facing ticket link as a staff member, use **Share Link** on the
ticket. This now contains a private access token. Do not post it publicly. In-game
notifications include the same capability. Integrations that construct player
URLs from ticket IDs must obtain a signed link instead; the panel endpoint is
`GET /api/v1/panel/tickets/{id}/access-link` with a staff session. Review this when
connecting an existing Minecraft plugin, since the plugin is outside these three
source repositories.

## Testing

Run these commands from the repository root. Docker supplies Java 21 and Node 22:

```sh
docker build --target test -t modl-backend-tests ./backend
docker build --target test -t modl-frontend-tests ./frontend
```

The backend suite includes password hash storage, owner registration closure,
login, session invalidation, BASE_URL-derived HTTP and WebSocket origin checks,
automatic signing-key persistence, AI usage counters, persistent uploads, traversal and
symlink rejection, expired/tampered file links, and size/type enforcement. The
frontend build runs TypeScript and ESLint; its test stage runs the existing tests.

For an acceptance test, use a **disposable** Compose project and database. The test
creates a known test owner and uploads a text file. Never run it against your real
instance. Stop your ordinary project first if it already occupies port 8080. Copy
`.env.example` to `.env` and leave the default local address:

```sh
docker compose -p modlcheck up --build -d --wait
docker run --rm --network modlcheck_private \
  -e E2E_DISPOSABLE=1 -e E2E_BASE_URL=http://frontend \
  modl-frontend-tests npm run test:e2e
```

The test races two first-owner registrations and requires exactly one success,
checks registration closes, tests wrong passwords, case-insensitive usernames, rejected email login, username changes, session
revocation after logout, credentialed HTTP preflights, authenticated binary
WebSocket exchange and origin rejection, and the upload/confirm/download flow.
Running it again uses the same disposable owner. Credentials are fixtures in the
test source, not production defaults.

Origin unit tests cover default ports, arbitrary public domains, IPv6, and rejection
of mismatched schemes, hosts, ports and malformed origins. HTTP and WebSocket
checks use the same BASE_URL-derived policy.

Inspect persistence after recreating the containers. The owner record is in the
`modl.instance_owner` collection, while settings and staff are in `modl_instance`.
Do not print credential hashes or API keys in shared logs. Verify a fresh browser
shows setup, registering opens `/panel`, and signing out then logging in works.
Use the backup and restore commands in [Persistence and backups](#persistence-and-backups) to test recovery.

When finished, delete **only this disposable project's** data:

```sh
docker compose -p modlcheck down --volumes
```

Replay verification can also be run against the disposable project using the
included synthetic format-v4 fixture. Set `E2E_SUITE=replay`,
`E2E_DISPOSABLE=1`, `E2E_BASE_URL` to the gateway, and `E2E_ORIGIN` to its configured
public origin when running `npm run test:e2e` from `frontend`. It exercises the
existing plugin upload API, local confirmation, public replay metadata, binary
downloads and HTTP ranges, storage listing, and retention settings. It never
requires a real player's recording. The fixture is documented in
[Replay test fixture](#replay-test-fixture).

Set `E2E_SUITE=usage` with `E2E_DISPOSABLE=1` to run the usage-settings acceptance
suite against empty disposable storage. It creates and removes synthetic files,
checks byte totals and downloads, exercises single/bulk deletion, and saves/toggles
retention with invalid-value and version-conflict checks. `E2E_KEEP_FILES=1` leaves
12 synthetic files for manual UI checks; remove the disposable project afterwards.
Never run this suite against operator data.

Set `E2E_SUITE=reset` with `E2E_DISPOSABLE=1` to run the destructive factory-reset
acceptance suite against a **fresh, separately named Compose project**. It rejects
anonymous requests and incorrect phrase/password, seeds an upload and settings,
requests reset, waits for the automatic backend restart, then verifies that old
credentials/sessions/uploads are gone and a new owner can register. Never run it
against an existing operator installation. The project must retain the Compose
backend restart policy. Backend unit tests additionally cover non-owner denial,
durable retry after cleanup failure, owner-last deletion, and symlink boundaries.

### Replay test fixture

The fixture is stored at `frontend/tests/fixtures/replay.modlreplay`.

Replay fixture generated with the backend's upstream replay-format 1.1.0 writer:
format version 4, Minecraft 1.21.4, one block at (0,64,0), and one player movement
at 1000 ms. Contains synthetic data only. Used to verify the existing upload and
viewer flow against persistent local storage.

## Validation results

Validated locally on 26–27 October 2026 using Docker Desktop with Linux containers.
The retained Java 21 / Spring Boot backend and React frontend passed:

- **974 backend tests**, **12 frontend tests**, TypeScript, ESLint, and production builds.
- Fresh-clone installation: copied the example environment, generated a secret,
  and started exactly MongoDB, backend, and frontend; all services became healthy.
  Both documented Docker test-image builds also passed from a separate clone.
- First-boot setup: concurrent registration produced exactly one owner and one
  conflict; MongoDB stored a salted BCrypt hash. Registration without email,
  matching password confirmation, an eight-character password, rejection of a
  seven-character password, dashboard access, logout and session revocation passed.
- Username-only accounts: email login is rejected; usernames are case-insensitive.
  Owner username changes persist and change the login name. Private staff invitation,
  activation and independent staff permissions passed; owner names cannot be reused.
  Existing account references and passwords are preserved.
- HTTP and realtime: the BASE_URL origin is trusted automatically; other origins
  are rejected. Live binary WebSocket exchanges pass through nginx. Unit tests
  cover schemes, hosts, default and custom ports, IPv6 and malformed origins.
- Automatic signing key: generation, legacy-key import, concurrent initialization,
  restrictive permissions, corrupt/symlink rejection and restart persistence passed.
  A no-secret disposable deployment retained signed file and ticket access after
  backend recreation. Native plugin startup through the v2 gateway returned the
  expected BASE_URL panel and WebSocket URLs; the plugin-style API-key WebSocket
  handshake and binary hello succeeded without a browser Origin header.
- Local uploads: presign, PUT, confirmation and exact download passed. Invalid
  signatures and disallowed types returned errors. Unit tests cover expiry,
  traversal, symlinks, restart persistence, size/type mismatches and overwrite protection.
- Player tickets: support creation without email passed. Private sharing links work;
  the ticket ID alone does not grant access. Raw chat remains private to staff.
- Usage settings: storage totals, file listing/download, single/bulk deletion,
  foreign-key rejection, retention save/toggle, invalid values and version conflicts
  passed on a disposable instance. Twelve synthetic files verified browser search,
  category filtering, size sorting and pagination. Retention Save displayed success.
  Deletion feedback checks the returned count, and refreshed lists prune stale selections.
  The page retains the upstream storage/replay/file controls. The restored upstream
  AI usage card shows successful provider requests for the current UTC month and
  a service breakdown, with no instance quota or cost display. Counts persist in
  MongoDB and start after this update; they are not historical Gemini billing totals.
- Replays: a synthetic recording passed upload/confirmation, metadata lookup,
  byte-for-byte download, HTTP ranges, storage listing and retention lookup.
  The browser rendered its block snapshot and played its one-second timeline.
  Replay, replay-lite and retention regression tests passed.
- Factory reset: a separate disposable instance verified authorization and phrase/password
  rejection, automatic restart, removed uploads/settings/accounts/sessions and reopened
  registration. Browser checks covered both confirmations, the disabled final button,
  and cancellation. Unit tests cover retry after cleanup failure and symlink boundaries.
- Recovery: stopped writes, dumped the whole database, copied uploads, replaced the
  disposable volumes, restored both, fixed ownership, and verified owner login and
  the three original uploaded files.
- Registration styling: browser inspection confirmed the landing page's 448px form
  width and locally served Inter/Audiowide fonts. Account settings show username only.

Acceptance tests used disposable projects; operator data was not reset. Historical
SMTP, hosted storage, tenant routing and commercial integrations were removed from
application code/configuration/dependencies. External upstream protocol packages
retain unused legacy wire fields; some internal account fields retain their
legacy names as described under [First setup and staff](#first-setup-and-staff).

Public certificate issuance, the external AI provider and a real Minecraft
server/plugin installation were **not exercised**. They need the operator's domain,
provider key or plugin. Review [Player ticket links](#player-ticket-links) before
connecting an existing plugin. Hosted-data migration was not attempted. Builds
still require the upstream shared-package registry.

## Source provenance and licensing

This distribution derives from these AGPL-3.0-only projects:

| Source | Commit used |
| --- | --- |
| https://github.com/modl-gg/panel | `e7e747e32edcd3d81ae90fdb85da8ce5215dc2e7` |
| https://github.com/modl-gg/backend | `57d51ec8aa97cde8eab54e7fa258b04012d28cf1` |
| https://github.com/modl-gg/landing | `58dd19d3aa545afcd0f06dfdb28ea14121ef9784` |

The panel is retained in `frontend`, and the Java backend in `backend`. The setup
page and its form derive from the landing repository's `Registration.tsx` and
`RegistrationForm.tsx`; unrelated site content is omitted. Original license texts
are retained. Modifications remain subject to the AGPL, including its source
availability obligations when the software is used over a network.

Builds still use upstream shared UI, protocol, and replay packages from the modl
package registry. The Java replay-format archive supplied upstream is retained in
`backend/libs`. Keep the lockfile when building or updating the frontend.

### Registration fonts

These are local copies of the Inter (400/500/600/700) and Audiowide (400) fonts
used by the original modl-gg landing registration page. Registration loads them
locally so its typography does not depend on Google Fonts being reachable.

Retrieved from Google's official font distribution at fonts.gstatic.com; license
texts are from google/fonts (`ofl/inter/OFL.txt`, `ofl/audiowide/OFL.txt`). Both use
the SIL Open Font License. The accompanying license files must remain with them.

The font license texts are retained beside the files in
[`frontend/client/public/fonts`](frontend/client/public/fonts).
