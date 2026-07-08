# Runbook: Linux VM + Docker Compose (production application server)

**Audience:** Operators deploying the stack on the **university application server** (`156.35.95.27`) using a **self-hosted GitHub Actions runner** and Docker Compose.

**Related:** [deploy-workflow-audit.md](deploy-workflow-audit.md), [../../docker/README.md](../../docker/README.md), [../../docker/scripts/README.md](../../docker/scripts/README.md).

---

## 1. Prerequisites

- **OS:** Linux x86_64 with Docker Engine and Compose plugin (`docker compose`).
- **Self-hosted runner:** Installed, online, labeled for `runs-on: self-hosted`; runner user in the `docker` group (or equivalent).
- **Network:** Outbound HTTPS to `github.com`; application server → LiteLLM on model server `156.35.160.78` (confirm port, default `4000`).
- **Repo:** Clone the monorepo to the path in GitHub Variable **`DEPLOY_DIR`** (same path the deploy workflow uses).
- **GitHub Variables:** `DEPLOY_DIR`, `DEPLOY_HEALTH_URL` (required). Enable **Pages → GitHub Actions** for documentation site.

---

## 2. Environment files on the server

Configuration is **not** in the deploy workflow; the server must have `.env` files:

| File | Purpose |
| ---- | ------- |
| `db/.env` | Postgres credentials |
| `rag-service/.env` | Spring, LiteLLM, JWT, OAuth, SMTP |
| `classifier-service/.env` | Classifier service |
| `webapp/.env` | Reverse-proxy ports, Next public build/runtime |
| `observability/.env` | Grafana password, observability ports |

Bootstrap from [`.env.example`](../../.env.example) and per-module `.env.example` files. **Never commit secrets.**

**Production rules:**

- `RAG_LLM_DEFAULT_PROVIDER=OPENAI_COMPATIBLE` and `LITELLM_BASE_URL` pointing at the model server.
- **No** direct Ollama URL for backend production.
- **No** Mailpit; use SMTP with `support.rag@gmail.es`.
- `SPRING_PROFILES_ACTIVE=prod,docker,infra` when observability is enabled.

---

## 3. Compose command (production server)

From repository root:

```bash
./docker/scripts/up.sh prod --server --obs --obs-private --no-env-prompt
```

Equivalent flags: `--server` merges `compose.prod-server.yml` (no public backend/classifier ports), implies remote model serving (no local Ollama profile), and blocks `--mail`.

---

## 4. Verify after deploy

1. **Containers:** `docker compose ps` (from `docker/` with the same `-f` chain) - services `running`.
2. **Health:** `curl -fsS "$DEPLOY_HEALTH_URL"` (same URL as GitHub Variable).
3. **Reverse proxy:** browser entry on `REVERSE_PROXY_HTTP_PORT` (default `80`).
4. **Readiness:** `chatProvider` = `OPENAI_COMPATIBLE` in actuator readiness (no Ollama probe).
5. **OAuth / SMTP / legal routes** - see post-deployment checklist in [`.env.example`](../../.env.example).

---

## 5. Rollback (manual)

```bash
cd "$DEPLOY_DIR"
git fetch origin
git reset --hard <previous-good-sha>
./docker/scripts/up.sh prod --server --obs --obs-private --no-env-prompt
curl -fsS "$DEPLOY_HEALTH_URL"
```

---

## 6. Observability (required in production)

Production deploy enables **`--obs --obs-private`**: OTEL collector, Jaeger, Prometheus, and Grafana run with **no host-published UI ports** (`compose.prod-obs.yml`). Access via Docker network or port-forward on the university network only.

See [../../observability/README.md](../../observability/README.md).

---

## 7. GitHub Actions vs manual

| Method | When |
| ------ | ---- |
| `push` to `main` or `workflow_dispatch` on `deploy.yml` | Standard production deploy (self-hosted runner) |
| `workflow_dispatch` on `self-hosted-runner-check.yml` | Validate runner, Docker, and Compose config |
| Manual on server | Hotfix rollback when Actions is unavailable - same compose command as above |
