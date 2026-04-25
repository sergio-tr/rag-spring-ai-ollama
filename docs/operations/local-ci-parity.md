# Local CI parity (conceptual)

**Purpose:** Operators run the same checks as GitHub Actions on their machine before pushing.

**How-to:** All commands, env vars, and script names live in [`.github/local/README.md`](../../.github/local/README.md). The CI pipeline is defined in [`.github/workflows/reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) and triggered by [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).

**Postgres image:** Pinned to `pgvector/pgvector:0.8.2-pg16-bookworm` everywhere; canonical shell export is `RAG_PLATFORM_POSTGRES_IMAGE` in [`.github/local/lib/common.sh`](../../.github/local/lib/common.sh).
