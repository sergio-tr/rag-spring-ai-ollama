# Classifier service: query type classification for RAG.
FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Application (app/ + entrypoint)
COPY main.py ./
COPY app/ ./app/
# Default model and trained models (models/default/ + trained)
COPY models/ ./models/
# Default datasets (optional; used for training or reference)
COPY data/ ./data/

ENV PORT=8000
ENV MODELS_DIR=/app/models
ENV DATA_DIR=/app/data

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
