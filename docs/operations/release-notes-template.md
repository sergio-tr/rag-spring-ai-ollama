# Release notes template (tag / release)

Use for annotated tags (e.g. `v0.x.y`). Paste into GitHub **Release** description or thesis appendix.

---

## VERSION — SHORT_TITLE

**Commit / tag:** `SHA_OR_TAG`  
**Date:** YYYY-MM-DD

### Highlights

- …

### Quality

- CI (`ci.yml`): …
- E2E fullstack (`e2e-fullstack.yml`): …
- Playwright API smoke (`npm run test:api` / optional `system-checks.yml`): … (not a `deploy.yml` gate unless policy changes)

### Deploy

- Target: Linux VM + `docker-compose.yml` + `compose.prod.yml` (see [runbook-docker-vm.md](runbook-docker-vm.md)).
- Deploy workflow gate: [deploy-workflow-audit.md](deploy-workflow-audit.md).
- Optional: repository secret `DEPLOY_HEALTH_URL` triggers a post-deploy HTTP check on manual deploy ([deploy-workflow-audit.md](deploy-workflow-audit.md)).

### Academic / thesis freeze (optional block)

Use when handing in a reproducible snapshot:

- **Annotated Git tag** (this release).
- **Commit SHA** repeated here; **GHCR image tags** pinned to the same SHA (not `latest`).
- **Evidence:** links or paths to CI run(s), `docker compose config -q` logs/obs/prod-like (see [observability-smoke workflow](../../.github/workflows/observability-smoke.yml)), smoke output, and health/readiness capture.
- **Topology:** primary reproducible path = Ollama on host or remote URL; GPU-in-Docker is optional (see [docker/README.md](../../docker/README.md)).

### Breaking changes

- … (or “None”.)

### Known limitations

- …

### Contributors

- …

---

## Tag procedure (Git)

Prefer semantic versions (e.g. `v1.2.0`):

```bash
git fetch origin
git checkout main   # or release branch
git pull
git tag -a v1.2.0 -m "Release: <one-line summary>"
git push origin v1.2.0
```

Create the GitHub **Release** from the tag and paste the filled template into the release notes. Align image tags with [`build-images.yml`](../../.github/workflows/build-images.yml) if the team versions images per release.
