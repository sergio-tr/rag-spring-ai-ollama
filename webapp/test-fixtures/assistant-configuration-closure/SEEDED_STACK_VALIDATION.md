# Seeded Stack Validation

Recorded: 2026-06-29 (closure evidence run)

## 1. Git status

- **HEAD:** `fdb651157`
- **Working tree:** Product closure work in progress; evidence harness added at `webapp/e2e/closure/assistant-configuration-thesis-screenshots.spec.ts` (untracked).
- **No product logic, scoring, dataset, migration, or RAG behavior edits** were made for this evidence pass.

## 2. Stack startup commands

Stack was already running via Docker dev lab preset. Recovery after backend classpath drift:

```bash
cd rag-service && rm -f target && ./mvnw clean compile -Dmaven.test.skip=true
./docker/scripts/dev-smoke-bootstrap.sh --skip-up --skip-compile
```

Canonical startup (documented):

```bash
./docker/scripts/up.sh dev --rag --proxy --classifier --no-env-prompt
# or
./docker/scripts/dev-smoke-bootstrap.sh
```

Playwright / API base URL:

```bash
PLAYWRIGHT_BASE_URL=https://127.0.0.1:8444
PLAYWRIGHT_SKIP_WEBSERVER=1
PLAYWRIGHT_IGNORE_HTTPS_ERRORS=1
```

## 3. Services checked

| Service | Container | Status |
|---------|-----------|--------|
| Reverse proxy | `docker-reverse-proxy-1` | Up (ports 80, 8444) |
| Webapp | `docker-webapp-1` | Up (healthy) |
| Backend (dev) | `docker-backend-dev-1` | Up (healthy after bootstrap) |
| PostgreSQL | `docker-postgres-1` | Up (healthy) |
| Classifier | `docker-classifier-service-1` | Up (healthy) |

## 4. Backend health

- **Liveness:** `GET https://127.0.0.1:8444/actuator/health/liveness` → `{"status":"UP"}` (after bootstrap)
- **Note:** Backend briefly returned 502 when `target/classes` was missing inside the bind-mounted volume (symlink to `/tmp/rag-phase-c-*` not visible in container). Resolved by host `mvnw clean compile` into a real `rag-service/target/` directory and `dev-smoke-bootstrap.sh --skip-up`.

## 5. Frontend health

- `GET https://127.0.0.1:8444/en/login` → HTTP 200

## 6. Gateway health

- HTTPS reverse proxy on `127.0.0.1:8444` routes `/en/*` → webapp and `/api/v5/*`, `/actuator/*` → backend-dev.

## 7. Authentication check

| User | Result |
|------|--------|
| `dev@local.test` / `dev` | Login OK; seed project present |
| `admin@dev.local` / `dev` | Login OK (ADMIN, dev profile seeder) |
| `admin@e2e.local` / `e2e` | Not seeded on dev profile (expected) |

## 8. Seeded data check

- Seed project `b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22` ("Default project") present.
- Selectable CHAT models returned via `GET /api/v5/me/llm/selectable-models?capability=CHAT` (preflight OK).

## 9. Known environment limitations

1. **Backend dev hot-reload fragility:** Bind-mounted `target/` symlinks pointing outside `rag-service/` break container compile/restart. Use a real `target/` directory before evidence runs.
2. **Admin credentials profile-dependent:** Dev stack uses `admin@dev.local`; e2e profile uses `admin@e2e.local`. Screenshot harness uses API login with dev defaults.
3. **ProviderRuntimeAcceptance** spawns host `mvnw test` subprocesses; fails with Maven permission errors in this WSL environment (not a product defect).
4. **Evaluation results screenshot** shows Lab UI export affordances; no live completed evaluation job was executed (campaigns prohibited).
