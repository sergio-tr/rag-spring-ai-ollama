# Product Evidence and Thesis Screenshot Closure

## 1. Executive verdict

**CONDITIONAL_PASS**

Configurable assistant product closure is evidence-complete: all thesis screenshots captured, language audit clean, seeded stack validated, and config API checks passing. Full `PASS` is withheld because `ProviderRuntimeAcceptance` Maven subprocess gates fail on host environment permissions (not product logic).

## 2. Scope

This closure converts the prior `CONDITIONAL_PASS` (code-complete, E2E/API blocked, screenshots missing) into an evidence package with:

- Seeded Docker dev stack validation
- Minimal Playwright API checks (config/settings/provider)
- Eleven thesis-safe PNG screenshots (`/en/`, light theme)
- Language audit, figure list, and stack/API reports

**Out of scope (prohibited):** evaluation campaigns, reindex, dataset/gold/scoring changes, RAG correctness, product feature work.

## 3. Seeded stack validation

See `SEEDED_STACK_VALIDATION.md`.

Summary: Docker lab stack (`dev --rag --proxy --classifier`) healthy after `dev-smoke-bootstrap.sh`. Backend required host `mvnw clean compile` when bind-mounted `target/` symlink broke container classpath. Seed login and project fixture verified.

## 4. E2E/API validation

See `E2E_API_VALIDATION_RESULT.md`.

| Check | Status |
|-------|--------|
| Stack preflight (liveness, login, models, project) | PASS |
| Config/schema/user/presets API | PASS (3/3) |
| ProviderRuntimeAcceptance live runtime | PASS |
| ProviderRuntimeAcceptance Maven subprocess evidence | FAIL (host permissions) |

**Final E2E/API:** CONDITIONAL_PASS

## 5. Screenshots captured

| Screenshot | File | Status | Thesis-safe | Notes |
|------------|------|--------|-------------|-------|
| Assistant configuration overview | `01_assistant_configuration_overview.png` | Captured | Yes | Compact summary, collapsed diagnostics |
| Assistant instructions | `02_assistant_instructions.png` | Captured | Yes | Three editable layers |
| Model configuration | `03_model_configuration.png` | Captured | Yes | Product provider label |
| Retrieval settings | `04_retrieval_settings.png` | Captured | Yes | Structured retrieval fields |
| Conversation memory & clarification | `05_conversation_memory_and_clarification.png` | Captured | Yes | Chat configuration panel |
| Evaluation setup | `06_evaluation_setup.png` | Captured | Yes | Lab overview |
| Single-model evaluation | `07_single_model_evaluation.png` | Captured | Yes | LLM evaluation page |
| Evaluation results & exports | `08_evaluation_results_exports.png` | Captured | Yes | UI affordances; no fabricated run |
| Admin model catalog | `09_admin_model_catalog.png` | Captured | Yes | Admin API login (`admin@dev.local`) |
| Source documents & evidence | `10_source_documents_evidence.png` | Captured | Yes | Chat config editor |
| Advanced technical details (appendix) | `appendix_advanced_technical_details.png` | Captured | Yes (appendix) | Only expanded technical screenshot |

## 6. Product language validation

- All screenshots captured under `/en/` with English UI labels.
- Product language guard unit tests remain part of the code-complete baseline (12/12 per prior closure).
- Screenshot audit: no forbidden internal nomenclature in principal figures (see `SCREENSHOT_LANGUAGE_AUDIT.md`).

## 7. Internal nomenclature check

Principal screenshots avoid: preset codes, snapshot/hash labels, Demo_* names, P0/P1/P15, raw provider enums, and "OpenAI-compatible" as primary label. Appendix screenshot intentionally shows collapsed **Advanced technical details** only.

## 8. Evaluation foundation preservation

No product logic, scoring, datasets, migrations, or RAG behavior were modified for this closure.

**Files touched for evidence only:**

- `webapp/e2e/closure/assistant-configuration-thesis-screenshots.spec.ts` (screenshot harness)
- Environment: `rag-service/target/` compile + `dev-smoke-bootstrap.sh` restart

Regression tests beyond environment/API checks were **not required** for product code (unchanged). Prior automated baselines remain authoritative: backend 78/78, frontend 140/140, product language guard 12/12.

## 9. Files changed

| Path | Purpose |
|------|---------|
| `webapp/e2e/closure/assistant-configuration-thesis-screenshots.spec.ts` | Thesis screenshot Playwright harness |
| `.cursor/evidence/assistant-configuration-closure-20260629/**` | Evidence package (this report + PNGs) |
| `rag-service/target/` (host compile) | Restore backend classpath for seeded stack |

## 10. Tests or checks executed

```bash
./docker/scripts/dev-smoke-bootstrap.sh --skip-up --skip-compile
cd webapp && PLAYWRIGHT_SKIP_WEBSERVER=1 npm run test:api -- --grep 'config-presets|ProviderRuntimeAcceptance'
cd webapp && PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test --project=chromium --grep '@evidence|@partial'
```

## 11. Known limitations

1. `ProviderRuntimeAcceptance` cannot complete Maven subprocess evidence on this host (permission denied).
2. Backend dev container unstable when `target/` is a symlink outside the bind mount; use real `target/` before evidence runs.
3. Screenshot 08 shows export UI without a completed evaluation job (campaigns prohibited).
4. Admin screenshot uses dev-profile admin (`admin@dev.local`), not e2e-profile admin.

## 12. Final thesis figure list

See `THESIS_FIGURE_LIST.md` — 10 principal figures + 1 appendix.

## 13. Recommended next step

**RAG correctness closure**

Evidence package is sufficient for thesis figures on configurable assistant product surfaces. Remaining gap is live API acceptance document generation (`ProviderRuntimeAcceptance` Maven gate), best addressed in a CI-like environment with unrestricted Maven execution—not a product change.
