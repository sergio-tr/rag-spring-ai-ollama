# Classifier service: query type classification for RAG.
# Base image and env from classifier-service/.env (CLASSIFIER_PYTHON_BASE_IMAGE, PORT, MODELS_DIR, DATA_DIR).
ARG BASE_IMAGE=nvidia/cuda:12.5.1-cudnn-runtime-ubuntu22.04
FROM ${BASE_IMAGE}

ARG PORT=8000
ARG MODELS_DIR=/app/models
ARG DATA_DIR=/app/data
ENV PORT=${PORT} MODELS_DIR=${MODELS_DIR} DATA_DIR=${DATA_DIR}

WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
     python3 \
     python3-pip \
     python3-venv \
     curl \
     libgomp1 \
  && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN python3 -m pip install --upgrade pip setuptools wheel \
  && python3 -m pip install --no-cache-dir -r requirements.txt

COPY uvicorn_entry.py ./
COPY app/ ./app/
COPY scripts/ ./scripts/
COPY models/ ./models/
COPY data/ ./data/

EXPOSE ${PORT}

CMD ["sh", "-c", "python3 ./scripts/fix-keras-vocab-utf8.py \"${MODEL_PATH:-${MODELS_DIR}/default/model.keras}\" || true; python3 -m uvicorn uvicorn_entry:app --host 0.0.0.0 --port ${PORT}"]