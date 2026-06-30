# Audit: `deploy.yml` — self-hosted runner, gates, and configuration

**Workflow:** [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)  
**Triggers:** `push` to `main`, `workflow_dispatch`.

---

## Summary

The workflow runs on a **self-hosted** runner on the application server (`156.35.95.27`). It syncs `DEPLOY_DIR` to `origin/main`, validates Compose, builds and starts the production stack locally, then curls `DEPLOY_HEALTH_URL`.

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
| `DEPLOY_DIR` | Git checkout path on the runner host | Must be a git repository |
| `DEPLOY_HEALTH_URL` | HTTP(S) health check after deploy | Reachable from the runner (e.g. reverse-proxy liveness URL) |

Optional documentation variables: `PRODUCTION_BASE_URL`, `FRONTEND_PUBLIC_URL`, `BACKEND_PUBLIC_URL`, `GITHUB_PAGES_URL`.

---

## Obsolete secrets

| Secret | Status |
| ------ | ------ |
| `VM_HOST` | Obsolete (SSH deploy removed) |
| `VM_USER` | Obsolete |
| `VM_SSH_KEY` | Obsolete |
| `VM_DEPLOY_DIR` | Replaced by Variable `DEPLOY_DIR` |
| `GHCR_TOKEN` | Optional — only if switching to prebuilt GHCR images instead of `--build` on server |

Application secrets (database, JWT, LiteLLM API key, OAuth, SMTP) belong in **server `.env` files**, not in GitHub, unless your ops model centralizes them in GitHub Environments.

---

## Related

- [runbook-docker-vm.md](runbook-docker-vm.md)
- [release-readiness-checklist.md](release-readiness-checklist.md)
- [../../docker/README.md](../../docker/README.md)
