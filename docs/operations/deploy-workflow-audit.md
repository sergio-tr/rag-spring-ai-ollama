# Audit: `deploy.yml` — gates, secrets, and proposed follow-ups

**Workflow:** [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)  
**Trigger:** `workflow_dispatch` only (manual).

---

## Summary

The workflow runs on `ubuntu-latest`, **gates** on a successful **`ci.yml`** run for the **current commit SHA** (that workflow runs the full DAG including stack integration, Playwright fullstack, and Sonar), then **SSH**s to the VM and runs **Docker Compose** (`docker-compose.yml` + `compose.prod.yml`) with `pull` and `up -d`.

**Selenium:** There is **no** `selenium.yml` in this repository and **no** Selenium step in `deploy.yml`. If a future policy reintroduces a browser gate, add it explicitly to `deploy.yml` and this audit.

**Strengths:** Single required workflow keeps the gate aligned with the full PR DAG; uses GitHub API to require **success** at the same `head_sha`; minimal permissions (`contents: read`).

**Post-deploy (implemented):** If repository secret `DEPLOY_HEALTH_URL` is set to an HTTP(S) URL reachable from GitHub Actions (e.g. public health endpoint behind the reverse proxy), the workflow runs `curl -fsS` after SSH deploy and **fails the job** on non-success. If the secret is **unset**, the step is skipped (documented skip).

**Remaining gaps:** Image references in Compose may still use `build:` locally; pinning prebuilt GHCR images by **commit SHA** tag is documented in [../../docker/README.md](../../docker/README.md) and [release-and-deploy.md](release-and-deploy.md). VM `.env` secrets are assumed pre-configured (see runbook).

---

## Gate (pre-deploy)

Implemented in the first step (`actions/github-script@v7`):

| Required workflow file | Role in quality model |
|------------------------|------------------------|
| `.github/workflows/ci.yml` | Full PR pipeline via `reusable-ci-core.yml`: backend, classifier, webapp, Playwright smoke, stack integration, fullstack E2E, Sonar, and performance on PRs to **main/master**. |

**Mechanics:** For each path, the script resolves the workflow id, lists runs for `head_sha: context.sha`, picks a run whose `head_sha` matches, and requires `status === 'completed'` and `conclusion === 'success'`.

**Not gated by deploy (by design today):** `integration.yml`, `e2e-fullstack.yml`, `sonar.yml` (manual), `build-images.yml`, `gatling.yml`, `micro-benchmark.yml`, `system-checks.yml`, `e2e.yml`. Promote any of these to **required** only if product policy demands it (adds friction to manual deploys).

---

## Secrets (repository)

| Secret | Use | Notes |
|--------|-----|--------|
| `VM_HOST` | SSH target | Prefer **DNS name**, not a literal IP, so Azure/public IP changes do not require doc edits. |
| `VM_USER` | SSH user | Service account with deploy rights only. |
| `VM_SSH_KEY` | Private key | Protect key rotation via GitHub **environments** if staging/prod split later. |
| `VM_DEPLOY_DIR` | `cd` before compose | Path to cloned repo on the VM (single string). |
| `GHCR_TOKEN` | `docker login ghcr.io` | Passed to stdin; actor is `${{ github.actor }}`. |
| `DEPLOY_HEALTH_URL` | Optional post-deploy HTTP check | If set, must be reachable from `ubuntu-latest`; job fails on curl error. Omit if no public URL yet. |

**Security notes:**

- `permissions: contents: read` — good default; no `id-token` unless OIDC is added later.
- Log output should **not** echo secret values; current script only echoes generic steps.
- Consider **environment** protection rules (required reviewers) for production VM secrets.

---

## Follow-up improvements (optional)

1. **Record metadata:** echo image digests or `docker compose images` to the step summary.
2. **Concurrency:** `concurrency: { group: deploy-vm, cancel-in-progress: false }` to avoid overlapping SSH deploys.
3. **Checkout on runner:** pass `git rev-parse HEAD` into the remote script and verify `VM_DEPLOY_DIR` matches the same SHA.
4. **Wider gate (policy):** add `integration.yml` to `required` if stack HTTP tests must block production deploys.

---

## Related

- [runbook-docker-vm.md](runbook-docker-vm.md)
- [release-readiness-checklist.md](release-readiness-checklist.md)
