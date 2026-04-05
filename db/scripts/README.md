# Database scripts

Manual **PostgreSQL** backup/restore against the default Docker container (`postgres`). Run from the **repository root**.

## Backup

Produces a `.sql` file with `pg_dump`:

```bash
./db/scripts/backup-db.sh
```

Optional environment variables:

- `DB_CONTAINER_NAME` (default `postgres`)
- `OUTPUT_DIR` (default `<repo>/backups`)
- `POSTGRES_USER` (default `postgres`)
- `POSTGRES_DB` (default `vectordb`)

## Restore

Destructive: applies the dump to the current DB in the container:

```bash
./db/scripts/restore-db.sh path/to/db-backup-YYYYMMDD-HHMMSS.sql
```

Same optional variables as backup (except `OUTPUT_DIR`).
