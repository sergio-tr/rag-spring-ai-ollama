# E2E critical path taxonomy

**Purpose:** Canonical list of Playwright specs tagged with **both** `@fullstack` and `@critical`. These run in CI job `e2e_fullstack` via `npm run test:e2e:fullstack:critical`.

**Workflow:** [`ci.yml`](../../.github/workflows/ci.yml) → [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) (job `e2e_fullstack`).

## Spec files and tests

| Area | Spec file | Test id / description |
| --- | --- | --- |
| Auth | `webapp/e2e/auth/auth-email-confirmation.spec.ts` | E2E-M2-01 … E2E-M2-04 (register, login blocked, confirm, login after confirm) |
| Chat | `webapp/e2e/chat/chat-rag.spec.ts` | E2E-05 upload, preset chat, sources, runtime and trace |
| Projects | `webapp/e2e/projects/projects-core.spec.ts` | E2E-S5-01 projects list/create; E2E-S5-02 documents page |
| Research / Lab | `webapp/e2e/research/classifier-lab.spec.ts` | E2E-08 classifier page state |
| Research / Lab | `webapp/e2e/research/lab-eval-pages.spec.ts` | E2E-13 RAG benchmark page; E2E-14 LLM evaluation page |
| Research / Lab | `webapp/e2e/research/lab-job-live-resume.spec.ts` | Live job panel survives refresh |
| Research / Lab | `webapp/e2e/research/lab-rag-projectless-corpus.spec.ts` | Evaluation corpus panel without active project |
| Research / Lab | `webapp/e2e/research/lab-ux-copy.spec.ts` | No forbidden API copy on LLM, embedding, RAG lab pages (3 paths) |
| Closure | `webapp/e2e/closure/lab-evaluation-navigation-resumption.spec.ts` | Evaluation survives navigate away and return |

## Notes

- API-only specs tagged `@critical` without `@fullstack` (e.g. under `webapp/e2e/api/`) run in `playwright_api_smoke`, not in `e2e_fullstack`.
- Closure and nightly specs under `e2e/closure/` without `@critical` are manual or optional.
- Evidence from local runs may be written under `exports/evaluation-evidence/` or via `EVIDENCE_DIR` when configured in test helpers.
