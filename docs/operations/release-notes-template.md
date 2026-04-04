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
