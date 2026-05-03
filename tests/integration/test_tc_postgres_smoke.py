"""
Local-only: PostgreSQL Testcontainers + same extension init as Java tests.

Requires Docker, INTEGRATION_USE_TESTCONTAINERS=1, and deps from tests/integration/requirements.txt.
Not used in CI (GitHub Actions Postgres service + HTTP-only pytest).
"""

from __future__ import annotations

import psycopg


def test_pgvector_extensions_match_java_init(tc_postgres_container: object) -> None:
    """Assert vector, hstore, and uuid-ossp exist after testcontainers-vectordb-init.sql."""
    from tc_postgres import psycopg_dsn_from_container

    dsn = psycopg_dsn_from_container(tc_postgres_container)
    with psycopg.connect(dsn) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT extname FROM pg_extension
                WHERE extname IN ('vector', 'hstore', 'uuid-ossp')
                ORDER BY extname
                """
            )
            found = {row[0] for row in cur.fetchall()}
    assert found == {"hstore", "uuid-ossp", "vector"}
