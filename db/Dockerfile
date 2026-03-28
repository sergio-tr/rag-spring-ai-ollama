# Base image from db/.env (POSTGRES_BASE_IMAGE). Must be Postgres 16 for server-dev-16.
ARG BASE_IMAGE=postgres:16
FROM ${BASE_IMAGE}

# Instalar herramientas necesarias para compilar
RUN apt-get update && apt-get install -y \
    git \
    build-essential \
    postgresql-server-dev-16 \
  && rm -rf /var/lib/apt/lists/*

# Clonar la extensión pgvector y compilarla
RUN git clone --depth 1 https://github.com/pgvector/pgvector.git /pgvector \
  && cd /pgvector \
  && make install

# Limpieza opcional
RUN rm -rf /pgvector

# El CMD y ENTRYPOINT quedan igual que la imagen base
