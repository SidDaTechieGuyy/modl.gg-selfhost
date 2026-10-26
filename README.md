# modl — self-hosted instance

The existing modl panel and Java backend, adapted for one operator on one domain.
The initial setup screen is adapted from the upstream registration page.

See [SELF_HOSTING.md](SELF_HOSTING.md) for installation, HTTPS, backups, and recovery.
See [UPSTREAM.md](UPSTREAM.md) for source versions and licensing.

```sh
cp .env.example .env
# Edit .env, including a randomly generated SESSION_SECRET.
docker compose up --build -d
```

Open http://localhost:8080 and create the owner account. The dashboard is at
`/panel`; subsequent logins use `/auth`. Only the first owner can register.

The repository contains two application folders:

- `frontend`: the original React panel, adapted setup page, and nginx gateway.
- `backend`: the original Java 21 / Spring Boot backend and its tests.

Runtime services are exactly `mongodb`, `frontend`, and `backend`.

See [TESTING.md](TESTING.md) for repeatable unit and disposable-stack acceptance tests.

Validation results are recorded in [VALIDATION.md](VALIDATION.md).
