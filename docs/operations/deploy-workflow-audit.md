# Audit: `deploy.yml` - self-hosted runner, gates, and configuration

**Workflow:** [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)  
**Triggers:** `push` to `main`, `workflow_dispatch`.

---

## Summary

The workflow runs on a **self-hosted** runner on the application server (`156.35.95.27`). It syncs `DEPLOY_DIR` to `origin/main`, bootstraps missing `.env` files from each module's `.env.example`, **writes GitHub repository secrets into those `.env` files** (`docker/scripts/apply_deploy_secrets.py`), validates Compose, builds and starts the production stack locally, then curls `DEPLOY_HEALTH_URL`.

**No SSH:** Legacy secrets `VM_HOST`, `VM_USER`, `VM_SSH_KEY`, and `VM_DEPLOY_DIR` are **obsolete**. Use repository **Variables** instead.

**CI gate:** Deploy assumes **branch protection** on `main` blocks merges unless [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) passes. Deploy does not poll CI (avoids deadlocks on post-merge pushes).

**Compose chain:** `docker-compose.yml` + `compose.obs.yml` + `compose.prod.yml` + `compose.prod-server.yml` + `compose.prod-obs.yml` with `--profile observability`, via:

```bash
./docker/scripts/up.sh prod --server --obs --obs-private --no-env-prompt
```

**Post-deploy:** `DEPLOY_HEALTH_URL` must be set; the job **fails** on curl error.

---

## Repository variables (required)

| Variable | Use | Notes |
| -------- | ----- | -------- |
| `DEPLOY_DIR` | Git checkout path on the runner host | Must be a git repository; `.env` files bootstrapped then secrets applied each deploy |
| `DEPLOY_HEALTH_URL` | Post-deploy health check | Optional; defaults to `https://127.0.0.1:8443/actuator/health/liveness` (uses `curl -k` for self-signed TLS) |
| `PRODUCTION_PUBLIC_HOST` | Public hostname or IP | Optional; default **`156.35.95.27`** |
| `PRODUCTION_HTTPS_PORT` | Host HTTPS port | Optional; default **`8443`** (set **`443`** in phase 2) |
| `PRODUCTION_HTTP_PORT` | Host HTTP port | Optional; default **`80`** |
| `LITELLM_BASE_URL` | LiteLLM on model server | Optional; default **`http://156.35.160.78:4000`** |
| `LITELLM_CHAT_MODEL` | Default chat model via LiteLLM | Optional; default **`qwen3.5:9b`** (must exist on LiteLLM) |
| `LITELLM_EMBEDDING_MODEL` | Default embedding model via LiteLLM | Optional; default **`bge-m3`** |

### Repository secrets (written into server `.env` on deploy)

| GitHub secret | Target file | Environment key |
| ------------- | ------------- | ---------------- |
| `POSTGRES_PASSWORD` | `db/.env` | `POSTGRES_PASSWORD` |
| `SPRING_DATASOURCE_PASSWORD` (or `POSTGRES_PASSWORD`) | `rag-service/.env` | `SPRING_DATASOURCE_PASSWORD` |
| `JWT_SECRET` (or `JWT_SERVICE`) | `rag-service/.env` | `RAG_JWT_SECRET` |
| `LITELLM_API_KEY` (or `OPENAI_API_KEY`) | `rag-service/.env` | `OPENAI_COMPATIBLE_API_KEY` |
| `MAIL_PASSWORD` | `rag-service/.env` | `SPRING_MAIL_PASSWORD` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_ID` | `rag-service/.env` | `RAG_AUTH_OAUTH_GOOGLE_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_ID` |
| `GOOGLE_CLIENT_SECRET` / `GOOGLE_OAUTH_CLIENT_SECRET` | `rag-service/.env` | `RAG_AUTH_OAUTH_GOOGLE_CLIENT_SECRET`, `GOOGLE_OAUTH_CLIENT_SECRET` |
| `ADMIN_PASSWORD` | `rag-service/.env` | `RAG_BOOTSTRAP_ADMIN_PASSWORD` |
| `GRAFANA_ADMIN_PASSWORD` | `observability/.env` | `GRAFANA_ADMIN_PASSWORD` |

Not mapped by deploy: `SONAR_TOKEN` (CI only), `SESSION_SECRET` (no current consumer in Compose/Spring).

Non-secret production settings (public URLs, `LITELLM_BASE_URL`, bootstrap admin email) remain in server `.env` from `.env.example` or manual edits; deploy does not overwrite existing non-secret keys.

Optional documentation variables: `PRODUCTION_BASE_URL`, `FRONTEND_PUBLIC_URL`, `BACKEND_PUBLIC_URL`, `GITHUB_PAGES_URL`.

---

## Obsolete secrets

| Secret | Status |
| ------ | ------ |
| `VM_HOST` | Obsolete (SSH deploy removed) |
| `VM_USER` | Obsolete |
| `VM_SSH_KEY` | Obsolete |
| `VM_DEPLOY_DIR` | Replaced by Variable `DEPLOY_DIR` |
| `GHCR_TOKEN` | Optional - only if switching to prebuilt GHCR images instead of `--build` on server |

Application secrets (database, JWT, LiteLLM API key, OAuth, SMTP) belong in **server `.env` files**, not in GitHub, unless your ops model centralizes them in GitHub Environments.

---

## Related

- [runbook-docker-vm.md](runbook-docker-vm.md)
- [release-readiness-checklist.md](release-readiness-checklist.md)
- [../../docker/README.md](../../docker/README.md)
