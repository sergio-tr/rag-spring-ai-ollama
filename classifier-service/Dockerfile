# Classifier service: query type classification for RAG.
# Base image and env from classifier-service/.env (CLASSIFIER_PYTHON_BASE_IMAGE, PORT, MODELS_DIR, DATA_DIR).
ARG BASE_IMAGE=python:3.11-slim
FROM ${BASE_IMAGE}

ARG PORT=8000
ARG MODELS_DIR=/app/models
ARG DATA_DIR=/app/data
ENV PORT=${PORT} MODELS_DIR=${MODELS_DIR} DATA_DIR=${DATA_DIR}

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Application (app/ + entrypoint)
COPY main.py ./
COPY app/ ./app/
# Runtime fix-ups for legacy model artifacts
COPY scripts/ ./scripts/
# Default model and trained models (models/default/ + trained)
COPY models/ ./models/
# Default datasets (optional; used for training or reference)
COPY data/ ./data/

EXPOSE ${PORT}

CMD ["sh", "-c", "python ./scripts/fix-keras-vocab-utf8.py \"${MODEL_PATH:-${MODELS_DIR}/default/model.keras}\" || true; uvicorn main:app --host 0.0.0.0 --port ${PORT}"]
