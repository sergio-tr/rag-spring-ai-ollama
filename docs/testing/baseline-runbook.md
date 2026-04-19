# Baseline runbook (local + CI parity)

Single place to reproduce the **same gates** CI uses for merge-quality checks. Module-specific troubleshooting stays in each module README.

## Prerequisites

| Tool | Notes |
|------|--------|
| **JDK 21** | Same as [`ci.yml`](../../.github/workflows/ci.yml) / [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml). |
| **Maven** | Use `./mvnw` from `rag-service/` (wrapper is authoritative). |
| **Python 3.11+** | Classifier CI uses 3.11; install dependencies from `classifier-service/requirements.txt` (`pip` / venv). |
| **Node.js** | [`webapp/package.json`](../../webapp/package.json) requires **Node ≥ 22**. Use **npm 11.x** with a lockfile-compatible toolchain (same major as CI). On developers’ machines where `npm ci` fails with lockfile drift, align Node/npm with CI before reporting a defect. |

### Backend environment (optional local parity)

Postgres-backed tests may require Docker (Testcontainers) or a reachable JDBC URL. CI sets variables in [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) and [`sonar.yml`](../../.github/workflows/sonar.yml). For JWT-related `@SpringBootTest`, ensure `RAG_JWT_SECRET` is long enough when overriding defaults.

---

## 1. rag-service (Java)

**Canonical gate:** clean verify with JaCoCo report + `jacoco:check`.

```bash
cd rag-service
./mvnw clean verify
```

**Latest baseline evidence (example):** exit **0**; aggregate `target/surefire-reports/TEST-*.xml` → **2309** tests, **52** skipped (typically `@EnabledIf` when Docker or Postgres prerequisites are absent), **0** failures.

**Artifacts:**

- `rag-service/target/site/jacoco/jacoco.xml` — consumed by Sonar Cloud ([`sonar-project.properties`](../../sonar-project.properties)).
- Surefire XML: `rag-service/target/surefire-reports/`.

**CI equivalence:** `core_backend` job runs `./mvnw verify` with Postgres service env (see workflow file).

---

## 2. classifier-service (Python)

**Canonical gate:** full test tree with coverage (matches `core_classifier`).

```bash
cd classifier-service
python3 -m pip install -r requirements.txt   # or use a venv
python3 -m pytest tests/ -v
```

**Artifacts:**

- `classifier-service/coverage.xml` — pytest-cov (`pytest.ini` / `.coveragerc`; `fail_under = 80`).

**CI equivalence:** [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) `core_classifier`. The **Sonar** job may run a **subset** of pytest for speed — see [`docs/quality/README.md`](../quality/README.md) (pytest markers row).

---

## 3. webapp (Next.js + Vitest)

**Canonical gate (matches documented chain):**

```bash
cd webapp
npm ci
npm run lint          # ESLint; warnings-only may still exit 0 — treat new errors as regressions
npm run typecheck     # tsc --noEmit
npm run test:coverage # Vitest + v8 thresholds (see vitest.config.ts)
npm run build         # next build
```

**Artifacts:**

- `webapp/coverage/lcov.info` — uploaded when Sonar scan runs.

**CI equivalence:** `core_webapp` / Sonar prep steps in [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml).

---

## 4. Sonar Cloud inputs (G4)

Sonar does **not** replace local gates; it aggregates reports produced by the steps above.

| Input | Produced by |
|-------|-------------|
| `rag-service/target/site/jacoco/jacoco.xml` | `./mvnw verify` |
| `classifier-service/coverage.xml` | `pytest` with cov |
| `webapp/coverage/lcov.info` | `npm run test:coverage` |

**Workflows:**

- **PR / main pipeline:** Sonar runs after core jobs in [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) (job `sonar`), triggered from [`ci.yml`](../../.github/workflows/ci.yml).
- **Manual / ad-hoc full scan:** [`sonar.yml`](../../.github/workflows/sonar.yml) (`workflow_dispatch`).

See also [../development/sonar-local-analysis.md](../development/sonar-local-analysis.md) for local analyst parity.

---

## 5. Where to record results

Update the **Baseline execution record** table in [../quality/README.md](../quality/README.md) when you re-run on a new commit or toolchain.

**Related normative docs:** [../quality/README.md](../quality/README.md), [../quality/merge-quality-gates.md](../quality/merge-quality-gates.md), [external-test-harness.md](external-test-harness.md).
