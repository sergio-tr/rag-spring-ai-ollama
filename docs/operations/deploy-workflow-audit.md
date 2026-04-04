# Audit: `deploy.yml` — gates, secrets, and proposed follow-ups

**Workflow:** [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)  
**Trigger:** `workflow_dispatch` only (manual).

---

## Summary

The workflow runs on `ubuntu-latest`, **gates** on **two** required workflows for the **current commit SHA**, then **SSH**s to the VM and runs **Docker Compose** (`docker-compose.yml` + `compose.prod.yml`) with `pull` and `up -d`.

**Selenium:** There is **no** `selenium.yml` in this repository and **no** Selenium step in `deploy.yml`. If a future policy reintroduces a browser gate, add it explicitly to `deploy.yml` and this audit.

**Strengths:** Clear gate list; uses GitHub API to require **success** at the same `head_sha`; minimal permissions (`contents: read`).

**Gaps:** No automated post-deploy health check; no image tag pinning in the script beyond `pull`; secrets are assumed pre-configured on the VM side for app config (see runbook).

---

## Gate (pre-deploy)

Implemented in the first step (`actions/github-script@v7`):

| Required workflow file | Role in quality model |
|------------------------|------------------------|
| `.github/workflows/ci.yml` | Primary unit/integration/webapp gate (incl. Playwright smoke). |
| `.github/workflows/e2e-fullstack.yml` | Full-stack browser E2E (`@fullstack`) and Playwright API coverage in that job’s scope. |

**Mechanics:** For each path, the script resolves the workflow id, lists runs for `head_sha: context.sha`, picks a run whose `head_sha` matches, and requires `status === 'completed'` and `conclusion === 'success'`.

**Not gated by deploy (by design today):** `integration.yml`, `sonar.yml`, `build-images.yml`, `gatling.yml`, `micro-benchmark.yml`, `system-checks.yml`, `e2e.yml`. Promote any of these to **required** only if product policy demands it (adds friction to manual deploys).

---

## Secrets (repository)

| Secret | Use | Notes |
|--------|-----|--------|
| `VM_HOST` | SSH target | Prefer **DNS name**, not a literal IP, so Azure/public IP changes do not require doc edits. |
| `VM_USER` | SSH user | Service account with deploy rights only. |
| `VM_SSH_KEY` | Private key | Protect key rotation via GitHub **environments** if staging/prod split later. |
| `VM_DEPLOY_DIR` | `cd` before compose | Path to cloned repo on the VM (single string). |
| `GHCR_TOKEN` | `docker login ghcr.io` | Passed to stdin; actor is `${{ github.actor }}`. |

**Security notes:**

- `permissions: contents: read` — good default; no `id-token` unless OIDC is added later.
- Log output should **not** echo secret values; current script only echoes generic steps.
- Consider **environment** protection rules (required reviewers) for production VM secrets.

---

## Proposed diff **after** deploy approval (optional improvements)

These are **optional** improvements; paste into a PR when agreed.

1. **Post-deploy smoke** (after SSH `up -d`): `curl -fsS` against the public health URL (from env, e.g. `DEPLOY_BASE_URL` secret) or `docker compose ps` + one internal check. Fail the job if unhealthy.
2. **Record metadata:** `echo` image digests or `docker compose images` to the step summary for audit trails.
3. **Concurrency:** `concurrency: { group: deploy-vm, cancel-in-progress: false }` to avoid overlapping SSH deploys.
4. **Checkout on runner (optional):** `actions/checkout@v4` to pass `git rev-parse HEAD` into the remote script and verify `VM_DEPLOY_DIR` matches the same SHA (guards stale clones).
5. **Wider gate (policy):** add `integration.yml` to `required` if stack HTTP tests must block production deploys.

Example **snippet** for (1) only — **not** applied in-repo until `HEALTH_URL` exists:

```yaml
      - name: Post-deploy HTTP smoke
        if: success()
        env:
          HEALTH_URL: ${{ secrets.DEPLOY_HEALTH_URL }}
        run: |
          test -n "$HEALTH_URL"
          curl -fsS -o /dev/null --max-time 30 "$HEALTH_URL"
```

---

## Related

- [runbook-docker-vm.md](runbook-docker-vm.md)
- [release-readiness-checklist.md](release-readiness-checklist.md)
