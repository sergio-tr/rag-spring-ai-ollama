# PR gate and trigger policy (CI)

This file defines **enforceable** CI policy for **protected branches** (`dev`, `main`) and how GitHub Actions triggers align with it.

## GitHub trigger policy (authoritative `CI` workflow)

[`ci.yml`](../workflows/ci.yml) is the only workflow that calls [`reusable-ci-core.yml`](../workflows/reusable-ci-core.yml).

| Event | Branches | Purpose |
| --- | --- | --- |
| `pull_request` | `dev`, `main`, `master` | **Pre-merge** validation (full DAG). This is the sole automatic way the full pipeline runs for work on `dev` and for PRs targeting `main` / `master`. |
| `push` | `main`, `master` only | **Post-merge** on the default line: re-runs the same DAG for the merge commit. **Required** so [`deploy.yml`](../workflows/deploy.yml) and any **SHA** gate can find a successful `CI` run for `head_sha` after a merge. |
| (not used) | `dev` on `push` | **Omitted** on purpose: with an open PR `dev` → `main`, a push to `dev` would fire **both** `push` and `pull_request` and duplicate the full DAG for the same commit. Use **PRs** for pre-merge coverage on `dev`. |

**Direct `git push` to `dev` without a PR** does not run `ci.yml`. Use a PR to `dev` (or a documented optional `workflow_dispatch` if the team adds one - not `push`+`pull_request` on the same branch).

## Required status checks for `dev` branch protection

- **Required (blocking)**: `CI / CI pipeline`

Notes:

- `Docker Compose CI` and `Observability smoke` are **path-filtered** and must not be treated as sufficient merge gates.

## Required status checks for `main` branch protection

- **Required (blocking)**: `CI / CI pipeline` (same check name as for `dev`).
- **Optional (additional)**: `Build (no tests) / Backend (Java) Build` from [`build.yml`](../workflows/build.yml) - fast compile-only; **not** a substitute for the full `CI` DAG. Enable only if the team wants the extra signal; the **primary** gate remains `CI / CI pipeline`.

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
- Fullstack UI (`@fullstack`)
 
Notes:

- Docker build smoke and SonarCloud are treated as **release-quality lanes** and run primarily on PRs targeting `main` / `master` (and on push to `main` / `master` for post-merge SHA contracts).

## SonarCloud policy

- **PRs to `main` / `master` (same repo)**: Sonar is **blocking** when `SONAR_TOKEN` is available.
- **Fork PRs**: Sonar is **non-blocking** (skipped) because secrets are not available by default.

## Docker Compose CI (path-filtered auxiliary)

[`docker-compose-ci.yml`](../workflows/docker-compose-ci.yml) runs when `docker/**` or that workflow file changes. **`pull_request`** covers `dev`, `main`, and `master`. It is an **auxiliary** validation (compose guard + representative `docker compose config -q`) and must not be treated as a substitute for the authoritative `CI` workflow.

## Manual deploy and commit SHA

[`deploy.yml`](../workflows/deploy.yml) runs on `workflow_dispatch` and **gates** on a **completed successful** run of [`.github/workflows/ci.yml`](../workflows/ci.yml) whose **`head_sha`** equals the commit being deployed. After a normal **merge to `main`**, the post-merge **`push`** triggers **`CI`** for the merge commit - that run satisfies the gate. **Do not** remove `push` on `main`/`master` from `ci.yml` without updating this contract.

## Mandatory failure artifacts (minimum)

- Backend Maven: upload Surefire/Failsafe reports on failure.
- Classifier pytest: upload `classifier-service/coverage.xml`.
- Webapp Vitest: upload `webapp/coverage/`.
- Playwright lanes: upload Playwright HTML report on failure; traces are collected on retry.
- Integration lanes: upload Spring logs on failure; classifier-required lane also uploads classifier logs.
- Compose structural guard: upload guard log on failure.

## Maintainer note (new workflows)

When adding or renaming a file under [`.github/workflows/`](../workflows/), update this policy if triggers or required checks change, and update the CI workflows summary in [../../docs/README.md](../../docs/README.md).

