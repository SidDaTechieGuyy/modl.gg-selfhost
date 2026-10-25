# modl.gg self-hosting stack

This project is a minimal self-hosting-friendly variant of the modl.gg stack. It keeps the product flow simple:
- first boot opens a registration screen
- the first user becomes the owner
- after that, the app requires login
- the frontend and backend run as separate services in Docker
- uploaded files are stored on a Docker volume instead of S3
- the app does not include billing, SMTP, Turnstile, or multi-tenant subdomain routing

## Stack

- frontend: Vite + React app in `frontend/`
- backend: Express + MongoDB in `backend/`
- database: MongoDB in `docker-compose.yml`
- storage: local Docker volume for uploads

## Prerequisites

- Docker Engine
- Docker Compose v2
- a domain or local hostname such as `modl.example.com` or `localhost`

## Setup

1. Copy `.env.example` to `.env` and adjust values.
2. Run:

```bash
docker compose up --build
```

3. Open the frontend in the browser.
   - local development: `http://localhost`
   - with proxy: `https://modl.example.com`

## First boot flow

On the first boot, the app shows a registration form. Create the first account as the owner. Once the owner exists, registration is closed and the app redirects the user to the login page.

After registration:
- the user lands in the panel
- uploads can be created through the panel
- logout returns the user to the login screen

## Environment configuration

The backend only keeps the variables needed for a self-hosted setup:

- `BASE_DOMAIN`
- `PORT`
- `MONGODB_URI`
- `JWT_SECRET`
- `SESSION_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `UPLOAD_DIR`

`CORS_ALLOWED_ORIGINS` accepts:
- `*` for any origin
- a comma-separated list such as `https://example.com,https://admin.example.com`
- dynamic matching via the incoming request origin when required by the custom browser logic

## Reverse proxy and HTTPS

Place the frontend behind a reverse proxy such as Caddy or nginx and terminate TLS there. The backend should remain internal to Docker unless you are intentionally exposing it directly.

Example Caddy config:

```caddy
modl.example.com {
  reverse_proxy frontend:80
}
```

If you keep the frontend on the same host as a reverse proxy, set `BASE_DOMAIN` to the external domain and keep the backend internal to Docker.

## Data persistence and backup

MongoDB and uploads are stored in Docker named volumes:

- `modl-mongo-data`
- `modl-uploads`

Back them up with standard MongoDB dump tooling or by copying the Docker volume data. For the upload volume, keep a regular snapshot or tarball backup of the mounted directory.

## Troubleshooting

- If the app cannot reach MongoDB, check that the container is healthy and that `MONGODB_URI` matches the service name `mongodb`.
- If CORS fails in the browser, make sure `CORS_ALLOWED_ORIGINS` includes the app origin or uses `*`.
- If a login fails, confirm the user exists and the password was stored as a bcrypt hash.
- If uploads fail, confirm the backend volume path exists and is writable.

## Notes

This repository intentionally removes the SaaS-era assumptions from the upstream project: no SMTP verification, no multi-tenant subdomain routing, no S3, no Turnstile validation, and no billing gates.
