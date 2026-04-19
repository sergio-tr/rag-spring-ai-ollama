# Testing strategy (overview)

**Workflows vs gates (canonical):** [../development/e2e-testing-strategy.md](../development/e2e-testing-strategy.md).

**Quality baseline, exclusions, Sonar baseline, and test policies (AP01):** [../quality/README.md](../quality/README.md).

**External test harness (Ollama / classifier HTTP / OTLP mocks, Wave 6.02):** [external-test-harness.md](external-test-harness.md).

## Quality gates before deploy (VM)

[`deploy.yml`](../../.github/workflows/deploy.yml) runs only on **`workflow_dispatch`** and **requires** successful runs of the following workflows on the **same commit SHA** as the deploy job (see [../operations/deploy-workflow-audit.md](../operations/deploy-workflow-audit.md)):

| Required before deploy | Workflow file | Role |
| --- | --- | --- |
| **CI** | [`ci.yml`](../../.github/workflows/ci.yml) | Backend `mvn verify`, classifier pytest, webapp lint/coverage/build, Playwright **smoke** (excludes `@fullstack`). |
| **E2E fullstack** | [`e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml) | Spring `e2e` profile + Postgres + Playwright **`@fullstack`** (browser). |

**Not blocking deploy by default:** `integration.yml`, `sonar.yml`, `gatling.yml`, `micro-benchmark.yml`, `system-checks.yml`, `build-images.yml`, `e2e.yml`. Promote any of these only if release policy demands it.

**Operational smoke after deploy:** manual checks (health, login/chat) are documented in [../operations/runbook-docker-vm.md](../operations/runbook-docker-vm.md) and the Deployment runbook section in [docker/README.md](../../docker/README.md) — not automated in `deploy.yml` today.

## Principles

- **Unit and service tests** live with each module (`rag-service`, `classifier-service`, `webapp`).
- **Stack HTTP integration** (`tests/integration/`) validates **contracts and multi-service behaviour** without a browser; run in CI via [`integration.yml`](../../.github/workflows/integration.yml).
- **E2E (browser)** is **Playwright** under `webapp/e2e/` (domain folders + `fixtures/` / `support/`; see [`webapp/e2e/README.md`](../../webapp/e2e/README.md)).
- **Naming:** “integration” in **Java** means Spring slice/JDBC tests inside `rag-service`; “**stack integration**” means **pytest + httpx** in `tests/integration/`.

## Testing matrix (what runs where)

| Layer | Purpose | Location | Typical CI |
| --- | --- | --- | --- |
| Unit | Fast, isolated | JUnit, classifier pytest, Vitest | [`ci.yml`](../../.github/workflows/ci.yml) |
| Integration (service) | Spring `@WebMvcTest`, JDBC | `rag-service/src/test` | `ci.yml` (`mvn verify`) |
| Stack integration (HTTP) | Auth, product API, lab jobs, optional classifier/obs | `tests/integration` | [`integration.yml`](../../.github/workflows/integration.yml) |
| E2E UI | Full product flows in browser | `webapp/e2e` (exclude `api/`) | `ci.yml` (smoke) + [`e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml) (`@fullstack`) |
| API / system smoke (Playwright `request`) | **Canonical** HTTP smoke: auth, product, serial API smoke chain — no browser | `webapp/e2e/api` | Local: `npm run test:api`; manual [`system-checks.yml`](../../.github/workflows/system-checks.yml); `make system-checks` |
| Load / stress | RPS, latency reports | Gatling | [`gatling.yml`](../../.github/workflows/gatling.yml) |
| Micro-benchmark (Python) | Low-concurrency RAG latency + estimated tokens (schema v1); not load | `tests/performance/retrieval_benchmark.py`, `llm_benchmark.py`, `infra_probe.py` | Local; optional [`micro-benchmark.yml`](../../.github/workflows/micro-benchmark.yml) (dispatch / weekly, **no gates**) |

**Playwright API vs pytest:** canonical operator/API smoke is [`webapp/e2e/api`](../../webapp/e2e/api) (`npm run test:api`). **pytest** in `tests/integration/` keeps **deep** HTTP contracts (lab jobs, obs, classifier matrices) — do not duplicate those assertions in Playwright API. Tooling reference: [traceability-legacy-tools.md](traceability-legacy-tools.md). See also [webapp/e2e/api/README.md](../../webapp/e2e/api/README.md), [tests/integration/README.md](../../tests/integration/README.md).

## Entry points

| Layer | Canonical doc |
| --- | --- |
| Backend verify (Surefire + JaCoCo) | [../../rag-service/README.md](../../rag-service/README.md) |
| External dependency mocks (rag-service testsupport) | [external-test-harness.md](external-test-harness.md) |
| Classifier pytest + coverage | [../../classifier-service/README.md](../../classifier-service/README.md) |
| Webapp unit / Playwright UI + API | [../../webapp/README.md](../../webapp/README.md), [../../webapp/e2e/api/README.md](../../webapp/e2e/api/README.md) |
| Integration (stack running) | [../../tests/integration/README.md](../../tests/integration/README.md) |
| Technical Compose smoke | [../../tests/e2e/README.md](../../tests/e2e/README.md) |
| Legacy tool traceability (k6 / Selenium / shell) | [traceability-legacy-tools.md](traceability-legacy-tools.md) |
| Python micro-benchmarks | [../../tests/performance/README.md](../../tests/performance/README.md) |
| Full pipeline script | [../../tests/full-stack-verify.sh](../../tests/full-stack-verify.sh) |

## CI

Authoritative workflow table: [../README.md](../README.md) (CI workflows section).

### CI parity (commands and policy)

Use the same commands locally that the reusable workflow runs (`./mvnw verify`, `pytest tests/`, `npm run test:coverage`, etc.). **Which jobs are blocking for merge**, how **fork PRs** interact with **SonarCloud**, and where **Compose / `NEXT_PUBLIC_*` defaults** are defined are documented in **[`docs/devops/README.md`](../devops/README.md)**.

### Coverage gates (commands, thresholds, CI artifacts)

| Module | Gate | Command (typical) | Report / artifact |
| --- | --- | --- | --- |
| **rag-service** | JaCoCo **line** coverage ≥ **80%** on the configured bundle (`rag-service/pom.xml` `jacoco:check`) | `./mvnw verify` (from `rag-service/`) | `rag-service/target/site/jacoco/jacoco.xml` (also `index.html`) |
| **classifier-service** | pytest-cov **lines** ≥ **80%**, branches on (`.coveragerc`) | `pytest` with project `addopts` | `classifier-service/coverage.xml`, `htmlcov/` |
| **webapp** | Vitest v8: **80%** lines, statements, functions, branches (`vitest.config.ts`) | `npm run test:coverage` (from `webapp/`) | `webapp/coverage/lcov.info`, `coverage/index.html` (CI may upload `webapp/coverage/` as an artifact — see `ci.yml`) |
| **SonarCloud** | Quality Gate (see Sonar UI); **Java + Python + TS LCOV** when `sonar.yml` runs | Same reports as above; workflow runs Vitest before scan | Dashboard + PR decoration; `sonar-project.properties` lists `jacoco.xml`, `coverage.xml`, `webapp/coverage/lcov.info` |

**Note:** JaCoCo and Sonar **coverage exclusions** (large orchestration, tools, etc.) mean the percentage is over **included** lines, not every file in the tree. Vitest `coverage.exclude` defines the frontend gate scope. See [../coverage/README.md](../coverage/README.md).

## React / Testing Library (webapp)

Stack: Vitest + `jsdom`, [`webapp/vitest.setup.ts`](../../webapp/vitest.setup.ts), tests as `src/**/*.test.{ts,tsx}`. Coverage thresholds in [`webapp/vitest.config.ts`](../../webapp/vitest.config.ts) apply to instrumented product code (`coverage.include` / `coverage.exclude`): **80%** lines, statements, functions, and branches. Component and hook tests should follow the same **behavior-first** philosophy as `src/lib` modules.

**Principle:** If a real user cannot perceive it, the test should not depend on it—except for pure modules under `src/lib`, where unit tests without DOM are appropriate.

### What to test (observable)

| Area | Focus |
| --- | --- |
| **Render** | Roles, accessible names, visible text, initial disabled/enabled state. Use `screen` and queries that mirror assistive tech. |
| **Interaction** | `userEvent.setup()` then `await user.click` / `type`. Assert **outcomes** (error message, navigation side effects visible in UI), not internal state. |
| **State** | Assert via DOM (`aria-*`, text), not by importing hooks or setters. |
| **Effects** | After `findBy*` / `waitFor`, assert loaders, loaded data, or error UI—not ref churn or effect call counts. |

### What not to test

- Internal CSS classes, Tailwind strings, or large HTML snapshots (unless accessibility is the only contract—then prefer role/label).
- Trivial getters or identity maps; cover business logic in `src/lib` or indirectly.
- Duplicating full Playwright flows; RTL stays **component- or screen-scoped** with mocks at boundaries.

### Query and event patterns

- Query priority: `getByRole` / `findByRole` > `getByLabelText` > `getByPlaceholderText` > `getByText` > `getByTestId` (last resort, stable `data-testid`).
- Async: `await screen.findByRole(...)` or `waitFor` after actions that trigger fetch/microtasks.
- **i18n:** Wrap with `NextIntlClientProvider` or mock `useTranslations` so assertions use stable strings.

### Mock strategy (boundaries)

| Boundary | Approach |
| --- | --- |
| API | `vi.mock` of modules that call [`api-client`](../../webapp/src/lib/api-client.ts) or TanStack Query hooks; or MSW if the team standardizes on one HTTP layer. Minimal JSON shapes that satisfy types. |
| TanStack Query | `QueryClient` + `QueryClientProvider` with `retry: false` and short cache in tests. |
| React context | Real providers with controlled values, or a future shared `renderWithProviders` (see below). |
| Next.js | Mock `next/navigation` only where the component uses it. |
| Zustand | Reset store in `beforeEach` or drive props from outside; avoid mixing store unit tests and UI tests without a clear split. |

**Rule:** Mock at the **edge** (API, router, i18n), not every internal function.

### Where to put tests

| Style | Use when |
| --- | --- |
| **Per component** | Reusable UI primitives; co-locate `*.test.tsx` next to the component or under `__tests__/`. |
| **Per feature** | User scenarios spanning multiple components under `features/<area>/`. |

`src/lib` tests stay next to modules; feature and API route tests live beside their modules (see `webapp/src/**.test.{ts,tsx}`). Shared helpers: [`webapp/src/test-utils/`](../../webapp/src/test-utils/).

### Conceptual examples (not production code)

- Login: fields visible by label/role → type with `user` → submit → `findByRole('alert')` or error text; do not assert `state.email` in the component.
- List after fetch: mock API → `findByRole('row', { name: /.../ })` or empty state message.
- Modal: `dialog` role → close by accessible name → `waitFor` until content is gone.

### Anti-patterns

- `container.querySelector('.css-class')` as the main assertion.
- `expect(instance.state)` or over-specifying hook internals (use `renderHook` only for reusable hooks without UI).
- Default `fireEvent` when `userEvent` suffices.
- Mocks that only return success—also cover loading and error paths.

### Shared utilities (deferred)

Prefer colocated wrappers in tests; optional shared helpers live under [`webapp/src/test-utils/`](../../webapp/src/test-utils/) (`IntlTestProvider`, `createTestQueryClient`). Global MSW is still optional.

ADR: [../adr/0004-react-testing-library-behavior-first.md](../adr/0004-react-testing-library-behavior-first.md).

### Coverage gate scope

[`webapp/vitest.config.ts`](../../webapp/vitest.config.ts) sets **`coverage.include`** to `src/**/*.{ts,tsx}` with **`coverage.exclude`** for: test files, type-only `src/types/api.ts`, [`src/test-utils/**`](../../webapp/src/test-utils/), Next.js **`page.tsx` / `layout.tsx`** (E2E-heavy), **shadcn** [`src/components/ui/**`](../../webapp/src/components/ui/), app **chrome** (layout/providers/settings shell), and a few **large UI modules** (e.g. `RagConfigForm`, classifier registry section, edit/delete project dialogs) documented in the config. **API route handlers** under `src/app/api/**` and **business hooks/components** remain in the gate.

Use **`npm run test:coverage`** and **`webapp/coverage/index.html`** to find remaining line/branch gaps inside included files.

### Fine gaps inside `src/lib` (HTML report)

1. Run `npm run test:coverage` from [`webapp/`](../../webapp/).
2. Open **`webapp/coverage/index.html`** for line- and branch-level highlighting (folder is gitignored).

Residual gaps are usually **branch arms** (error handling, optional fields). Prioritize by product risk (auth, chat, projects, lab).
