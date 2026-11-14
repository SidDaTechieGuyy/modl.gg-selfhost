# modl.gg — self-hosted instance

An unofficial self-hosting adaptation of [modl.gg](https://github.com/modl-gg).
Run your moderation panel, support tickets, appeals, knowledge base and replays
on your own domain, with your own database and files.

## What is different from upstream?

- **One repository:** the panel, registration page and Java backend are combined.
- **Three Docker services:** frontend, backend and MongoDB.
- **Username and password accounts:** no SMTP, email verification or magic-link login.
  Passwords are securely hashed. Registration requires at least eight characters
  and matching password confirmation. Optional passkey login remains available.
- **First-owner setup:** registration closes after the owner account is created.
- **One domain:** no subdomain-based tenant routing or separate registration site.
- **Local uploads:** files and replays stay in a persistent Docker volume; no S3 account.
- **No billing or subscriptions:** payment integrations and premium gates are removed.
- **No registration CAPTCHA:** Cloudflare Turnstile and hosted signup steps are removed.
- **Simpler configuration:** browser API and WebSocket origins follow `BASE_URL`;
  signing keys are generated and saved automatically.
- **Usage settings:** local storage, replay retention and AI request counts remain,
  without subscription quotas or cost cards. Gemini AI moderation is optional.
- **Factory reset:** the owner can reset the instance from settings after confirming
  the reset phrase and their password.

This is an adaptation of the existing app, not an official modl.gg release.
Use the `main` branch for the general self-hosted version.

## Quick start

You need Docker Engine with Docker Compose, or Docker Desktop. An Intel/AMD Linux
host is supported by the published images. Allow at least 4 GB of RAM for the
stack, plus storage for your database and uploads.

```sh
git clone https://github.com/SidDaTechieGuyy/modl.gg-selfhost.git modl-selfhost
cd modl-selfhost
cp .env.example .env
```

Edit `.env`:

| Setting | What to enter |
| --- | --- |
| `BASE_URL` | Your public address, such as `https://modl.example.com`. For local use, keep `http://localhost:8181`. Include a port only when visitors actually use it. No path or trailing slash. |
| `MONGODB_URI` | Leave the supplied value for the included MongoDB container. |
| `GEMINI_API_KEY` | Optional. Leave empty unless you want Gemini AI moderation. |

```sh
docker compose pull
docker compose up -d
```

Open **http://localhost:8181** on the Docker host, or use your configured HTTPS
domain. Create the owner username and password, then enter the panel. Invite staff
from settings. Subsequent visits use the normal login flow.

The localhost port is bound to `127.0.0.1`, so it is not accessible directly from
other machines. Configure a reverse proxy for public access. The backend and
MongoDB do not publish host ports.

No session secret needs to be entered for a new installation. If upgrading an old
installation that already sets `SESSION_SECRET`, retain that value for its first
successful startup so the existing key can be saved automatically.

## Public access with HTTPS

Point your domain at your server and configure a reverse proxy. Set `BASE_URL`
to the same public HTTPS address. API calls, uploads and WebSockets use that one
domain; no separate public backend or WebSocket port is needed.

For **Caddy running on the Docker host**, use:

```caddyfile
modl.example.com {
    reverse_proxy 127.0.0.1:8181
}
```

Allow ports 80 and 443 to reach Caddy for public access and certificate issuance.
If your proxy runs in a container instead, connect it to the app's Docker network
and forward to `frontend:80`. With nginx, enable WebSocket upgrade forwarding and
set upload-size limits appropriate for your evidence files.

After changing `.env`, run `docker compose up -d` to apply it.

## Connect your Minecraft server

Install the upstream [modl Minecraft plugin](https://github.com/modl-gg/minecraft/releases)
compatible with your Minecraft version. Complete web registration first, then copy
its API key from **Settings → General → Server Config**.

For a standalone Paper/Spigot server, put this in `plugins/modl/boot.yml`:

```yaml
mode: standalone
api-key: "YOUR_PANEL_API_KEY"
testing-api: false
```

Point the **Minecraft process** at your own instance using either:

```sh
MODL_API_URL=https://modl.example.com
```

or a JVM option before `-jar`:

```sh
java -Dmodl.api.url=https://modl.example.com -jar paper.jar nogui
```

On DiscoPanel, append the `-Dmodl.api.url=...` option under **Properties → JVM Options**.
Restart Minecraft afterward. Use the domain only, without `/api` or version suffixes.
The plugin must be able to reach it and trust its HTTPS certificate. Do not use the
plugin's hosted registration wizard for this self-hosted instance.

The plugin has hooks for **GrimAC, Vulcan and Polar**. Replay recording is opt-in:
configure `replay-enabled` and `replay-auto-record` in `plugins/modl/bridge-config.yml`.
Exact compatibility depends on the plugin and Minecraft versions.

**Ticket links:** use the panel's **Share Link** action for player-facing tickets.
Links need their private access token; integrations that construct links from just
a ticket ID may need adapting. Keep these links private.

## Updates and Docker images

To update an installation using the default images:

```sh
git pull
docker compose pull
docker compose up -d
```

Back up before updating. GitHub Actions builds and tests images on pushes to `main`
and through **Actions → Test and publish Docker images → Run workflow**. It publishes
`ghcr.io/siddatechieguyy/modl-frontend:latest` and `modl-backend:latest`, plus matching
Git commit tags. Wait for the complete workflow to succeed before deploying.
For rollback, replace `:latest` in both image references with the same previous
successful full commit SHA. Image rollback does not reverse database changes.

For forks, publish your own images and set `MODL_IMAGE_OWNER` to your lowercase
GitHub owner name. Make the packages public or configure GHCR pull credentials.

To **build locally**, use `docker-compose-dev.yml` instead. It builds production
containers from source but publishes no host ports; supply your own port mapping
or reverse proxy. Builds still require access to upstream modl package registries.

## Backups and persistence

MongoDB uses the `mongodb_data` volume; uploads use `uploads_data`. Docker prefixes
these with the Compose project name. Keep that name unchanged when redeploying.
Normal restarts preserve data. **`docker compose down --volumes` deletes it.**

Back up the whole MongoDB instance and the entire uploads directory, including
hidden files and metadata. The hidden `.instance-secret` file keeps existing signed
file and ticket links valid. Keep `.env` in a private backup too.

Example maintenance backup, using a Linux/macOS shell:

```sh
mkdir -p backups
docker compose stop frontend backend
docker compose exec -T mongodb mongodump --archive --gzip > backups/mongodb.archive.gz
docker compose cp backend:/app/uploads backups/uploads
docker compose start backend frontend
```

Use a fresh backup directory each time and keep a copy outside the server.
To restore into an empty installation, restore `.env` first, then:

```sh
docker compose up -d mongodb
docker compose create backend
docker compose exec -T mongodb mongorestore --archive --gzip --drop < backups/mongodb.archive.gz
docker compose cp backups/uploads/. backend:/app/uploads
docker compose run --rm --no-deps --user root --entrypoint sh backend -c 'chown -R 1001:1001 /app/uploads'
docker compose up -d
```

Restoring replaces the target data. This guide covers this fork's backups;
migrating a hosted modl.gg installation requires a separate data migration.

## Troubleshooting

- **Backend unhealthy:** run `docker compose logs --tail=100 backend`. The backend's
  internal health endpoint is `http://localhost:8080/v1/health`, not host port 8181.
- **Login, uploads or WebSockets fail:** ensure `BASE_URL` matches the address in
  your browser, and that your reverse proxy forwards WebSocket upgrades.
- **Minecraft reports a certificate error:** check the certificate chain from the
  Minecraft container. Do not disable certificate verification.
- **Registration is closed:** the instance already has an owner; log in instead.
- **Uploads fail:** check disk space, proxy size limits and uploads ownership (UID 1001).
- **AI is unavailable:** set `GEMINI_API_KEY` if you want that optional provider.

The owner can use **Settings → General → Server Config → Reset everything** to
return to first-boot setup. It permanently deletes accounts, settings, moderation
records, tickets, replays and uploads. The confirmation requires the reset phrase
and owner password. Backups and Docker configuration are kept.

## Credits and licence

Based on [panel](https://github.com/modl-gg/panel),
[backend](https://github.com/modl-gg/backend) and
[landing](https://github.com/modl-gg/landing) by modl.gg. Original licences are
retained; this project is distributed under **AGPL-3.0-only**. See [LICENSE](LICENSE).
Keep the corresponding modified source available when distributing or hosting it
as required by that licence.

The registration page includes Inter and Audiowide fonts under the SIL Open Font
License. Their licence files are retained beside the fonts in
[`frontend/client/public/fonts`](frontend/client/public/fonts).
