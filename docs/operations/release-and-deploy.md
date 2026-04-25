# Release and deploy (overview)

## Version control

Application source lives in this monorepo; **behavioural truth** is code + migrations + CI configuration.

## Images

Backend, classifier, webapp, and infra images are **Linux OCI** images. [`.github/workflows/build-images.yml`](../../.github/workflows/build-images.yml) pushes each service to GHCR with:

- **`<github_sha>`** (full commit SHA) — **canonical tag** for reproducible deploy and rollback documentation. Prefer `docker pull ghcr.io/<owner>/rag-spring-ai-ollama-<service>:<SHA>` when consuming images from [`build-images.yml`](../../.github/workflows/build-images.yml). Repository Compose files under `docker/` use **`build:`** with local contexts; pin bases via `.env` (see [`docker/README.md`](../../docker/README.md)).
- **`latest`** — updated on each successful build; **not** a contract for production rollback; use only for ad-hoc convenience.

Digest may be recorded in release notes as extra evidence; the **primary** operational reference is the SHA tag.

**Operator view:** [../../docker/README.md](../../docker/README.md) deployment section.

## Quality gates

Workflows for tests, SonarCloud, and optional E2E/load jobs are listed in [../README.md](../README.md). **Manual VM deploy** (`deploy.yml`) requires prior green runs of `ci.yml` and `e2e-fullstack.yml` on the same commit — see [deploy-workflow-audit.md](deploy-workflow-audit.md). **Deploy-specific gate table:** [../testing/README.md](../testing/README.md) — [Quality gates before deploy (VM)](../testing/README.md#quality-gates-before-deploy-vm). Thresholds (e.g. coverage) are enforced in build config; see `rag-service/pom.xml`, classifier `pytest`/`coverage` config, and Sonar project properties.

## Database

Schema changes ship via **Flyway** in `rag-service`; database image init under `db/init/` applies at first container creation.

**Detail:** [../../db/README.md](../../db/README.md)
