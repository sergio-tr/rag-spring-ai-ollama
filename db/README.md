# Base de datos (PostgreSQL + pgvector)

PostgreSQL 16 con la extensión pgvector. Usada por el backend (rag-service) para vectores y datos del RAG.

## Variables (db/.env)

| Variable           | Descripción      | Por defecto  |
|--------------------|------------------|-------------|
| `POSTGRES_PORT`    | Puerto expuesto  | `5432`      |
| `POSTGRES_USER`    | Usuario          | `postgres`  |
| `POSTGRES_PASSWORD`| Contraseña       | `postgres`  |
| `POSTGRES_DB`      | Nombre de la BD  | `vectordb`  |

## Uso con Docker Compose

En `docker/docker-compose.yml` el servicio `postgres` se construye desde **db/** (`context: ../db`, `dockerfile: Dockerfile`) y carga **db/.env** (`env_file: ../db/.env`). El backend usa las mismas variables para `SPRING_DATASOURCE_*` (también con `env_file: ../db/.env`).

Desde la raíz del repo (para que Compose use las variables de db/.env al sustituir en el fichero):

```bash
cd docker
docker compose --env-file ../db/.env up -d
```

O bien copia `db/.env` a `docker/.env` y ejecuta `docker compose up -d` desde `docker/`.

## Solo levantar la BD (desarrollo local)

```bash
docker build -t rag-db ./db
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  rag-db
```

Luego en el backend (rag-service) usa en `.env` o en el entorno: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vectordb`, mismo usuario y contraseña que en db/.env.
