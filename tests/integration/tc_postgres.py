"""
Optional PostgreSQL via Testcontainers for local checks (INTEGRATION_USE_TESTCONTAINERS=1).

Does not run in CI: [.github/workflows/integration.yml](../../.github/workflows/integration.yml)
uses the GitHub Actions Postgres service instead.

Init SQL is aligned with Java Testcontainers: rag-service/src/test/resources/testcontainers-vectordb-init.sql
"""

from __future__ import annotations

from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

_REPO_ROOT = Path(__file__).resolve().parents[2]
_INIT_SQL = _REPO_ROOT / "rag-service/src/test/resources/testcontainers-vectordb-init.sql"


def _run_init_sql(conn: Any) -> None:
    sql = _INIT_SQL.read_text(encoding="utf-8")
    with conn.cursor() as cur:
        for part in sql.split(";"):
            stmt = part.strip()
            if not stmt or stmt.startswith("--"):
                continue
            cur.execute(stmt)
    conn.commit()


def start_pgvector_container() -> Any:
    """Start pgvector/pg16, apply extension init, return started container (caller must stop)."""
    from testcontainers.postgres import PostgresContainer

    container = PostgresContainer(
        "pgvector/pgvector:pg16",
        username="postgres",
        password="postgres",
        dbname="vectordb",
        driver=None,
    )
    container.start()

    import psycopg

    dsn = container.get_connection_url()
    conn = psycopg.connect(dsn, autocommit=False)
    try:
        _run_init_sql(conn)
    finally:
        conn.close()

    return container


def jdbc_url_from_container(container: Any) -> str:
    """Spring Boot JDBC URL for the running container (host port mapped to localhost)."""
    url = container.get_connection_url()
    u = urlparse(url.replace("postgresql+psycopg://", "postgresql://").replace("postgresql+psycopg2://", "postgresql://"))
    if u.scheme not in ("postgresql", "postgres"):
        u = urlparse(url)
    user = unquote(u.username or "")
    password = unquote(u.password or "")
    host = u.hostname or "localhost"
    port = u.port or 5432
    db = (u.path or "/").lstrip("/") or "postgres"
    return f"jdbc:postgresql://{host}:{port}/{db}?user={user}&password={password}"


def psycopg_dsn_from_container(container: Any) -> str:
    """psycopg v3 connection string (no SQLAlchemy driver prefix)."""
    url = container.get_connection_url()
    for prefix in ("postgresql+psycopg2://", "postgresql+psycopg://"):
        if url.startswith(prefix):
            return "postgresql://" + url[len(prefix) :]
    return url
