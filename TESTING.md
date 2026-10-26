# Verification

Run these commands from the repository root. Docker supplies Java 21 and Node 22:

```sh
docker build --target test -t modl-backend-tests ./backend
docker build --target test -t modl-frontend-tests ./frontend
```

The backend suite includes password hash storage, owner registration closure,
login, session invalidation, HTTP origin reflection with credentials, all supported
origin settings, WebSocket origin validation, persistent uploads, traversal and
symlink rejection, expired/tampered file links, and size/type enforcement. The
frontend build runs TypeScript and ESLint; its test stage runs the existing tests.

For an acceptance test, use a **disposable** Compose project and database. The test
creates a known test owner and uploads a text file. Never run it against your real
instance. Stop your ordinary project first if it already occupies port 8080. Copy
`.env.example` to `.env`, set a random secret, and leave the local address/origin:

```sh
docker compose -p modlcheck up --build -d --wait
docker run --rm --network modlcheck_private \
  -e E2E_DISPOSABLE=1 -e E2E_BASE_URL=http://frontend \
  modl-frontend-tests npm run test:e2e
```

The test races two first-owner registrations and requires exactly one success,
checks registration closes, tests wrong passwords, both login identifiers, session
revocation after logout, credentialed HTTP preflights, authenticated binary
WebSocket exchange and origin rejection, and the upload/confirm/download flow.
Running it again uses the same disposable owner. Credentials are fixtures in the
test source, not production defaults.

To exercise the reflective policies against the running gateway, change
`CORS_ALLOWED_ORIGINS` in `.env` to `*`, recreate the backend with the same Compose
command, and add `-e E2E_PERMISSIVE_ORIGINS=1` to the test command. Repeat for
`dynamic`. Restore the explicit local origin afterwards. Unit tests also cover
comma-separated lists, malformed origins, and default ports.

Inspect persistence after recreating the containers. The owner record is in the
`modl.instance_owner` collection, while settings and staff are in `modl_instance`.
Do not print credential hashes or API keys in shared logs. Verify a fresh browser
shows setup, registering opens `/panel`, and signing out then logging in works.
Use the backup and restore commands in `SELF_HOSTING.md` to test recovery.

When finished, delete **only this disposable project's** data:

```sh
docker compose -p modlcheck down --volumes
```
