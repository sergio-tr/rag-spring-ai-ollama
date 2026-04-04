# End-to-end tests (repository layout)

**Naming:** `tests/e2e/` here is **Compose/shell glue**, not the Playwright suite. **Browser E2E** specs live in **`webapp/e2e/`** (see [docs/testing/README.md](../../docs/testing/README.md)).

This folder holds **technical / Compose** E2E glue. **Product UI E2E** (Playwright) lives under **`webapp/e2e`**.

| Path | What it covers |
|------|----------------|
| `e2e-technical-compose.sh` | Docker Compose stack smoke (`rag-service/scripts/smoke-test.sh`) + warns if unauthenticated `GET {product}/presets` is not **401/403**. |
| `webapp/e2e/*.spec.ts` | Browser flows: auth, projects, chat, documents, presets, lab, settings, admin gates. Strategy: [docs/testing/README.md](../../docs/testing/README.md); commands: [webapp/README.md](../../webapp/README.md). |

**Related automation (not Playwright):**

- HTTP integration: `tests/integration/` (pytest + stack).
- System / API smoke (Playwright `request`): `cd webapp && npm run test:api` (`make system-checks`). Traceability: [docs/testing/traceability-legacy-tools.md](../../docs/testing/traceability-legacy-tools.md).
- Load: `tests/gatling/` (see [docs/performance/README.md](../../docs/performance/README.md)).
