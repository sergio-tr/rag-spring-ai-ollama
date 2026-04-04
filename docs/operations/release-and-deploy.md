# Release and deploy (overview)

## Version control

Application source lives in this monorepo; **behavioural truth** is code + migrations + CI configuration.

## Images

Backend, classifier, webapp, and infra images are **Linux OCI** images. Build and publish steps are in `.github/workflows/` (e.g. `build-images.yml`). Exact tags and promotion rules belong in workflow comments or team runbooks on the module side if extended.

**Operator view:** [../../docker/README.md](../../docker/README.md) deployment section.

## Quality gates

Workflows for tests, SonarCloud, and optional E2E/load jobs are listed in [../README.md](../README.md). **Manual VM deploy** (`deploy.yml`) requires prior green runs of `ci.yml` and `e2e-fullstack.yml` on the same commit — see [deploy-workflow-audit.md](deploy-workflow-audit.md). **Deploy-specific gate table:** [../testing/README.md](../testing/README.md) § Quality gates before deploy (VM). Thresholds (e.g. coverage) are enforced in build config; see `rag-service/pom.xml`, classifier `pytest`/`coverage` config, and Sonar project properties.

## Database

Schema changes ship via **Flyway** in `rag-service`; database image init under `db/init/` applies at first container creation.

**Detail:** [../../db/README.md](../../db/README.md)
