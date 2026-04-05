# Runbook: Linux VM + Docker Compose (deploy target)

**Audience:** Operators deploying the stack from this repository to a **Linux VM** (e.g. Azure VM) using the same compose files as local **prod-style** runs.

**Related:** [deploy-workflow-audit.md](deploy-workflow-audit.md), [azure-vm-parameterization.md](azure-vm-parameterization.md), [../../docker/README.md](../../docker/README.md), [../../scripts/README.md](../../scripts/README.md).

---

## 1. Prerequisites

- **OS:** Linux x86_64 with Docker Engine and Compose plugin (`docker compose`).
- **Network:** Outbound HTTPS to `ghcr.io` (image pull) and GitHub (if cloning/pulling via HTTPS).
- **Repo:** Clone the monorepo to the path stored in GitHub secret `VM_DEPLOY_DIR` (same path used in [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)).
- **Auth to GHCR:** The deploy workflow runs `docker login ghcr.io` using `GHCR_TOKEN`; the VM must be able to **pull** images your workflow publishes (package visibility + token scopes).

---

## 2. Environment files on the VM

Application configuration is **not** fully defined in `deploy.yml`; the VM must have consistent `.env` files expected by Compose overlays. Typical layout (adjust to your team):

| File / area | Purpose |
|-------------|---------|
| `db/.env` | Postgres credentials and DB name. |
| `rag-service/.env` | Spring datasource, Ollama URL, JWT, etc. |
| `classifier-service/.env` | Classifier API keys and model paths. |
| `webapp/` | Next.js runtime env if not only build-time (see [webapp/README.md](../../webapp/README.md)). |

**Rule:** Never commit secrets; use the VM filesystem or a secret manager; align variable names with [docker/README.md](../../docker/README.md). Bootstrap from each module’s **`.env.example`** (placeholders such as `CHANGE_ME`); replace with strong values before production — do not ship default passwords from examples.

---

## 3. Compose command (matches deploy workflow)

From `VM_DEPLOY_DIR`:

```bash
docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml pull || true
docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml up -d
```

**Image tags:** Prebuilt GHCR images use the **commit SHA** as the primary tag (see [release-and-deploy.md](release-and-deploy.md)). For reproducible deploys, pin Compose `image:` references or `docker pull` to `ghcr.io/<owner>/rag-spring-ai-ollama-<service>:<SHA>` — do **not** rely on `latest` as the rollback contract.

Use `docker compose ps` and service logs for troubleshooting: `docker compose logs -f <service>`.

---

## 4. Verify after deploy

1. **Containers:** `docker compose ps` — all required services `running` (or expected exit for one-shot jobs).
2. **HTTP:** Hit the reverse proxy or app URL (parameterize hostname — see [azure-vm-parameterization.md](azure-vm-parameterization.md)).
3. **Backend:** Actuator/health if exposed per [rag-service/README.md](../../rag-service/README.md).

---

## 5. Rollback (manual)

```bash
cd "$VM_DEPLOY_DIR"
git fetch origin && git checkout <previous-good-sha>
docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml pull || true
docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml up -d
```

Ensure `.env` files remain compatible with that revision.

---

## 6. Observability on the same VM (optional)

To run the observability stack **alongside** the product on one host:

- Follow [../../observability/README.md](../../observability/README.md) for `observability/.env` and port matrix.
- Add overlays: `compose.obs.yml` (and logs/infra if used), with the same `--env-file` pattern as documented in the observability README.

Treat observability as an **optional Compose overlay**; production parity is “product + reverse proxy + prod compose”; observability is a **documented add-on**, not a hard requirement for minimal deploy.

---

## 7. GitHub Actions vs manual

| Method | When |
|--------|------|
| `workflow_dispatch` on `deploy.yml` | After **`ci.yml`** + **`e2e-fullstack.yml`** are green on the **same SHA** (see [deploy-workflow-audit.md](deploy-workflow-audit.md)). |
| SSH manual | Hotfix, rollback, or when Actions is unavailable — use the same compose commands as above. |
