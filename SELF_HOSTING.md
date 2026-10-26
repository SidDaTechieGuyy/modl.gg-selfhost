# Self-hosting modl

## Prerequisites

Use a Linux server with Docker Engine and the Docker Compose plugin, or Docker
Desktop with Linux containers on Windows/macOS. Allow at least 4 GiB RAM and enough
disk space for uploaded evidence and database backups. Java and Node are supplied
by the build containers; they need not be installed on the host. Initial builds
require internet access to the image, dependency, and upstream modl registries.

For public access, point a domain at your server and install Caddy or nginx on the
host. Keep ports 80 and 443 available to that proxy. MongoDB and the backend do not
publish host ports; the gateway listens only on `127.0.0.1:8080`.

## Install and configure

Clone **this converted repository**, then enter its root:

```sh
git clone <your-repository-url> modl-selfhost
cd modl-selfhost
cp .env.example .env
openssl rand -hex 32
```

Put the generated value in `SESSION_SECRET`, then edit the other settings:

| Variable | Meaning |
| --- | --- |
| `BASE_URL` | Public address including its scheme and optional port, with no path or trailing slash. Use `http://localhost:8080` locally or your `https://…` address publicly. |
| `MONGODB_URI` | Database connection; the supplied Compose address works without modification. |
| `SESSION_SECRET` | At least 32 random characters. Signs file and player-ticket capabilities. Preserve it with backups. |
| `CORS_ALLOWED_ORIGINS` | One origin, a comma-separated list, `*`, or `dynamic`. Origins include scheme and port, and must not contain a path or trailing slash. |
| `GEMINI_API_KEY` | Optional. Needed only if you enable the external AI moderation provider. |

For example, a public instance can use:

```dotenv
BASE_URL=https://moderation.example.org
CORS_ALLOWED_ORIGINS=https://moderation.example.org
```

The `*` and `dynamic` settings both accept valid incoming HTTP(S) origins and echo
the actual origin, with credentials enabled. An explicit list trusts only those
origins. The same policy governs HTTP, browser request checks, and WebSockets.
Opaque origins such as `null` are rejected. Native plugin WebSockets may omit
Origin but still require their API key. Browsers can independently restrict
third-party cookies; serving the panel and API on the supplied single domain
avoids that problem. HTTPS sessions use Secure, HTTP-only, SameSite=None cookies;
local HTTP sessions use SameSite=Lax.

```sh
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 backend
```

All three services should become healthy. Locally, visit http://localhost:8080.
For a public instance, configure HTTPS below before creating the owner account.

## First setup and staff

The first visit displays the registration page. Supply a server name, owner
username, email, and password. Passwords require at least 12 characters and at most
72 UTF-8 bytes, and are stored as salted BCrypt hashes. Email is an account
identifier; no delivery service is needed.

Registration initializes the existing roles, settings, forms, and default content,
then establishes a session and opens `/panel`. Log out and use `/auth` to log back
in with the owner username or email and password. Registration returns a conflict
after the owner exists, and `/register` redirects to login. Concurrent registration
requests are serialized by MongoDB's unique owner identifier. If setup is
interrupted after the owner is saved, log in again to retry initialization.

Invite staff from settings, then use **Share invitation link** in the pending
invitation's menu. Share that link privately. The recipient sets their own password
and logs in using the invited email. Links expire after 24 hours. Existing role
permissions still apply. Panel display names are separate from the owner's login
username.

Set the Minecraft plugin's panel address to the same `BASE_URL`, and obtain its
API key from the panel's server settings. Native `/v1/…` routes and browser
`/api/v1/…` routes both reach the backend. Realtime uses the same domain at
`/api/v1/realtime/ws`, with its scheme derived from `BASE_URL`.

New player tickets receive an access capability in their browser. Keep the
original ticket link/browser data; a ticket identifier alone does not grant access.

## HTTPS with Caddy

Run Caddy on the host with this Caddyfile, replacing the example hostname:

```caddyfile
moderation.example.org {
    reverse_proxy 127.0.0.1:8080
}
```

Caddy handles certificates and WebSocket upgrades. Set both `BASE_URL` and the
allowed origin to `https://moderation.example.org`, then run:

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
in `modl_instance`; back up the whole MongoDB instance, not just the database name
at the end of the URI. Include upload sidecar metadata and your `.env` secret.

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

Use the original `SESSION_SECRET`; changing it invalidates existing signed file
and ticket links. This conversion targets a fresh instance. Importing an existing
hosted database or its external files requires a separate data migration.

## Troubleshooting

- **Docker cannot connect:** start Docker Desktop/Engine. On Windows, verify WSL
  and virtualization are installed and any required restart has completed.
- **Build cannot download a modl package:** check DNS and connectivity to
  `nexus.modl.gg`. The shared upstream packages are still build dependencies.
- **Backend unhealthy:** inspect `docker compose logs backend` and MongoDB health.
  Check that the secret is at least 32 characters and the database address resolves
  from inside the container.
- **Registration says it is closed:** the owner record already exists. Log in;
  redeploying containers intentionally does not reset ownership.
- **Login or uploads fail after enabling HTTPS:** make `BASE_URL` match the actual
  public scheme, host, and port. Recreate the backend after editing `.env`.
- **Origin rejected:** list the exact browser origin in `CORS_ALLOWED_ORIGINS`, or
  use the documented reflective settings. Apply changes with `docker compose up -d`.
- **Realtime disconnects:** preserve proxy upgrade headers and a read timeout
  above the heartbeat interval. The WebSocket address uses the same public domain.
- **Upload fails:** check disk space, size limits, and volume ownership (UID 1001).
  Expired upload links must be requested again. Uploads publish only after their
  full declared byte count has been received.
- **AI unavailable:** ordinary moderation works without a provider key. Configure
  the optional key before enabling AI review.

## Verification

The backend retains its unit tests and adds password, registration, origin, and
local-storage coverage. The frontend retains its build and tests. Run them with
the supplied Java 21 and Node 22 build environments, then test a fresh Compose
project before an upgrade. Runtime acceptance requires registration, logout, login,
an upload, and a WebSocket connection through the gateway; a successful build alone
does not establish those results.

To obtain a player-facing ticket link as a staff member, use **Share Link** on the
ticket. This now contains a private access token. Do not post it publicly. In-game
notifications include the same capability. Integrations that construct player
URLs from ticket IDs must obtain a signed link instead; the panel endpoint is
`GET /api/v1/panel/tickets/{id}/access-link` with a staff session. Review this when
connecting an existing Minecraft plugin, since the plugin is outside these three
source repositories.
