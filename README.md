# modl.gg self-hosted

This repository is a self-hosting-friendly single-domain build of the modl.gg stack. It removes the SaaS-only assumptions from the upstream codebase while keeping the same backend language and framework family.

## What this repo contains

- `backend/`: Java Spring Boot service
- `frontend/`: React + Vite panel UI
- `docker-compose.yml`: MongoDB, frontend, and backend services
- `README.md`: setup and deployment guide

## Key self-hosting decisions

- single domain instead of multi-tenant subdomain routing
- password registration/login instead of email verification or magic links
- local uploads on a Docker named volume instead of S3
- no Stripe, billing, SMTP, Turnstile, or premium gating
- CORS and WebSocket origins controlled via `CORS_ALLOWED_ORIGINS`

## Prerequisites

- Docker Engine
- Docker Compose v2
- a hostname or domain like `modl.example.com` or `localhost`

## Quick start

1. Copy `.env.example` to `.env` and update the values.
2. Start everything:

```bash
docker compose up --build
```

3. Open the frontend in a browser:
   - local: `http://localhost`
   - via proxy: `https://modl.example.com`

## First-boot flow

- The first time the app loads, the registration screen is visible.
- The first account created becomes the owner.
- Once an owner exists, registration closes and the app redirects to the login page.
- After login, the user lands in the panel and can upload files.

## Environment variables

The backend keeps only the values needed for a self-hosted setup:

- `BASE_DOMAIN`
- `PORT`
- `MONGODB_URI`
- `JWT_SECRET`
- `SESSION_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `UPLOAD_DIR`

`CORS_ALLOWED_ORIGINS` accepts:
- `*`
- a comma-separated list such as `https://example.com,https://admin.example.com`
- dynamic matching based on the incoming origin

## Reverse proxy and HTTPS

Place the frontend behind a reverse proxy such as Caddy or nginx and terminate TLS there. The backend remains inside Docker unless you intentionally expose it.

Example Caddy config:

```caddy
modl.example.com {
  reverse_proxy frontend:80
}
```

## Data persistence and backup

The compose file creates named volumes for:

- MongoDB data
- upload files

Back up those Docker volumes on a schedule.

## Troubleshooting

- If the app cannot connect to MongoDB, verify the `MONGODB_URI` points to the `mongodb` service.
- If browser requests fail, confirm the domain is present in `CORS_ALLOWED_ORIGINS`.
- If uploads fail, ensure the backend container can write to `UPLOAD_DIR`.
- If registration cannot complete, verify there is no owner account already in MongoDB.

## Tests

The backend includes unit tests for:

- owner registration closure
- login and logout behavior
- password hashing
- CORS / WebSocket origin validation
- local upload storage

Run them with:

```bash
cd backend
./mvnw test
```

Note: this repository was intentionally simplified to a self-hosting-friendly single-domain flow. It removes the SaaS-specific behavior from the upstream codebase while keeping the same backend stack and a working local deployment model.
