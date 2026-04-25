# Dev PR gate (CI policy)

This file defines the **enforceable** CI policy for pull requests targeting branch `dev`.

## Required status checks for `dev` branch protection

- **Required (blocking)**: `CI / CI pipeline`

Notes:

- `Docker Compose CI` and `Observability smoke` are **path-filtered** and must not be treated as sufficient merge gates.

## Required lanes on PR to `dev`

The `CI` workflow delegates to the reusable DAG in `.github/workflows/reusable-ci-core.yml`.

### Baseline lanes (always required)

- Backend: `rag-service` Maven `./mvnw -B clean verify`
- Classifier: `classifier-service` `pytest tests/` (coverage produced via `pytest.ini`)
- Webapp: `webapp` `lint`, `typecheck`, `test:coverage`, `build`, `doc`
- Playwright UI smoke: `npm run test:e2e` (invert `@fullstack`)
- Playwright API smoke: `npm run test:api`

### Deep lanes (required after baseline passes)

- Stack integration (pytest, classifier optional)
- Stack integration (pytest, classifier required)
- Compose structural guard
- Docker build smoke (no push)
- Fullstack UI (`@fullstack`)
- SonarCloud scan (internal PRs; see below)

## SonarCloud policy

- **Internal PRs (same repo)**: Sonar is **blocking** when `SONAR_TOKEN` is available.
- **Fork PRs**: Sonar is **non-blocking** (skipped) because secrets are not available by default.

## Mandatory failure artifacts (minimum)

- Backend Maven: upload Surefire/Failsafe reports on failure.
- Classifier pytest: upload `classifier-service/coverage.xml`.
- Webapp Vitest: upload `webapp/coverage/`.
- Playwright lanes: upload Playwright HTML report on failure; traces are collected on retry.
- Integration lanes: upload Spring logs on failure; classifier-required lane also uploads classifier logs.
- Compose structural guard: upload guard log on failure.

