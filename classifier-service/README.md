# Classifier Service

Query-type classification service for the RAG backend. It exposes an HTTP API used by the RAG service to classify user questions (e.g. `COUNT_DOCUMENTS`, `SUMMARIZE_MEETING`).

**Target architecture (frozen model):** [Classifier System](../docs/architecture/target-architecture.md).  
**Default model status:** sklearn C3 (`metadata.json` in `models/default/`). Historical freeze: [Classifier status freeze](../docs/research/classifier-status-freeze.md) (superseded 2026-07-02).

## Architecture

- **Domain models** (`app/models/`): `ClassificationResult`, `ModelMetadata`, `TrainingResult`, `ErrorDetail` - value objects for responses and errors.
- **Exceptions** (`app/exceptions.py`): `ValidationError` (400), `ModelNotFoundError` (404), `ClassificationError` (503), `TrainingError` (500).
- **Inference** (`app/inference/`): `ModelLoader` (loads and caches sklearn or legacy Keras model + labels per `metadata.json`), `InferenceEngine` (runs prediction).
- **Registry** (`app/registry/`): `ModelRegistry` (list models, resolve paths, register trained models).
- **Training** (`app/training/`): `TrainingPipeline` (Excel → train → save → register).
- **Evaluation** (`app/evaluation/`): `EvaluationPipeline` (metrics + classification report and confusion matrix PNGs), `EvaluationResult`.
- **Services** (`app/services/`): `ClassificationService`, `ModelRegistryService`, `TrainingService`, `EvaluationService` - orchestration; routes call these only.
- **Routes** (`app/routes.py`): Thin HTTP layer; delegates to services and maps exceptions to structured error responses.

All code comments are in English.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/health` | Service status; includes whether default model is loaded. |
| GET | `/models` | List available models (default first, then trained). |
| POST | `/classify` | Body `{"query": "...", "modelId": "default"}` (modelId optional). Returns `{"queryType": "COUNT_DOCUMENTS"}` etc. All JSON in **camelCase**. |
| POST | `/train` | Multipart: Excel (Question, QueryType) + `model_name` (tag), optional `labels` (JSON array) or `labels_file` (.txt). Optional `epochs`, `batch_size`, optional `owner_id` (stored in `metadata.json` when `MODELS_DIR` is shared). Response: `modelId`, `name`, `metrics` (camelCase). |
| POST | `/evaluate` | Query params `modelId`, `includeImages`; optional evaluation Excel upload. If no file is uploaded, uses `{DATA_DIR}/evaluation_dataset.xlsx`. Returns `modelId`, `metrics` (classificationReport, accuracy, macroAvg, confusionMatrix, classNames), optional base64 images (camelCase). |
| GET | `/evaluate/{model_id}/report.png` | Classification report heatmap PNG for the model (path segment = model id). |
| GET | `/evaluate/{model_id}/confusion.png` | Confusion matrix heatmap PNG for the model. |

- **Use classifier by tag:** send `modelId` in `POST /classify` (query param or body); use **camelCase** in all request/response JSON for interoperability with Java/JavaScript.
- **Train with fixed labels:** send `labels` as a JSON string array (e.g. `["COUNT_DOCUMENTS","SUMMARIZE_MEETING"]`) or upload a `labels_file` (one label per line, like `query_type_labels.txt`). Class order is preserved.
- **Evaluation:** `POST /evaluate` returns full metrics (classification_report, accuracy, macro_avg, confusion_matrix, class_names) and optionally `classificationReportImageBase64` and `confusionMatrixImageBase64` for the webapp (display or download). Upload an Excel file to override the default dataset; otherwise the service reads `{DATA_DIR}/evaluation_dataset.xlsx` and returns a structured 400 `EVALUATION_ERROR` if it is missing. The GET image endpoints return the PNGs directly for use in `<img src="...">` or download links.

Error responses use a consistent shape: `{"code": "...", "message": "...", "details": {...}}` (optional).

## Datasets

Default datasets are under `data/`:

- `basic_dataset_qa_clasificacion_final.xlsx` - training dataset (213 rows; `Pregunta`/`Question`, `QueryType`).
- `evaluation_dataset.xlsx` - default evaluation dataset used by `/evaluate` when no file is uploaded (`Pregunta`/`Question`, `QueryType`).

These were moved from `rag-service/src/main/resources/python/` and are the single source of truth here.

## Run locally

```bash
pip install -r requirements.txt
# Ensure models/default/model.joblib (sklearn default) and models/default/labels.txt exist
uvicorn uvicorn_entry:app --host 0.0.0.0 --port 8000
```

Or with Docker (from repo root):

```bash
docker compose -f docker/docker-compose.yml up -d classifier-service
```

### Docker image size

The **default** image uses `python:3.10-slim-bookworm` and `requirements-runtime.txt` only (sklearn 1.7 + FastAPI, **no TensorFlow**). It is about **600 MiB** and builds in under a minute on a typical CI runner.

The default `model.joblib` (sklearn C3) is trained with **scikit-learn 1.7.x** on Python 3.10 - no Python 3.11 or CUDA base is required for serving.

> **Default artifact:** **LinearSVC** (installed via `scripts/train_sklearn_classifier.py --variant C3`).  
> HTTP `POST /train` (Lab custom models) uses **LogisticRegression** (`SklearnTrainingPipeline`) — different estimator from the shipped default.

Legacy Keras `model.keras` is **not** loaded when `metadata.json` declares `modelType: sklearn`. A superseded C1 artifact may be kept under `models/archive/` for reference only.

The previous default (`nvidia/cuda` base + `tensorflow[and-cuda]`) pulled **~10 GiB** because CUDA was installed twice (OS image + pip wheels) even though the production default model is **sklearn** (`models/default/model.joblib`).

| Profile | Base image | Python deps | Approx. size |
| --- | --- | --- | --- |
| Default (sklearn serving) | `python:3.10-slim-bookworm` | `requirements-runtime.txt` | ~600 MiB |
| GPU / Keras Lab | `nvidia/cuda:12.5.1-cudnn-runtime-ubuntu22.04` | runtime + `requirements-gpu.txt` | ~8–10 GiB |

Enable the GPU stack only when training or serving legacy Keras models:

```bash
./docker/scripts/up.sh dev --classifier-gpu   # sets CLASSIFIER_INSTALL_GPU_EXTRAS=1
```

Local dev / CI pytest: `pip install -r requirements.txt` (includes CPU TensorFlow for unit tests).

The RAG backend is configured via `RAG_CLASSIFIER_SERVICE_URL` (default `http://localhost:8000`; in Docker `http://classifier-service:8000`).

## Environment

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | 8000 | Server port. |
| `UVICORN_HOST` | `127.0.0.1` | Bind address for `python uvicorn_entry.py` (local). Set to `0.0.0.0` when the process must listen on all interfaces (Docker/Compose already pass `--host 0.0.0.0` to uvicorn). |
| `MODELS_DIR` | models | Directory for default and trained models. |
| `DATA_DIR` | data | Directory for default datasets. |
| `DEFAULT_MODEL_ID` | default | Model id used when none is specified. |
| `MODEL_PATH` | `{MODELS_DIR}/default/model.joblib` | Default model file (sklearn `model.joblib` or Keras `model.keras`; see `metadata.json`). |
| `LABELS_PATH` | `{MODELS_DIR}/default/labels.txt` | Default labels file. |

## Tests

- **`tests/unit/`** - unit tests (model, inference, config, isolated services).
- **`tests/test_api.py`** - API tests with `TestClient` (no external network).
- **`tests/regression/`** - manual regression against a running HTTP service (`capture_baseline.py` / `check_baseline.py`) and pytest for harness logic (`test_baseline_lib.py`). See `tests/regression/README.md` and **Manual regression testing** below.

```bash
pytest tests/ -v
```

SonarCloud reads `coverage.xml` from the repository root perspective (`sonar-project.properties`). After pytest generates the report, run `python scripts/patch_coverage_xml_for_sonar.py` so Cobertura `<source>` is `classifier-service/app` (CI and `.github/local/ci-like-sonar.sh` do this automatically).

## Manual regression testing

**Goal:** detect behavior changes in **classifier-service** after code or model changes.

The harness lives **inside the service**: `classifier-service/tests/regression/` (separate from unit tests in `tests/unit/` and API tests in `tests/test_api.py`).

### Acceptance criteria

- `mismatchesCount == 0` when checking against the baseline.
- If the service returns `503` (model not loaded), the check may fail. Check `GET /health`.

### Fixed dataset

- `classifier-service/tests/regression/questions.txt`

### Step 1: Capture baseline

From **`classifier-service/`** with the service running:

```bash
cd classifier-service
python tests/regression/capture_baseline.py \
  --classifier-base-url http://localhost:8000 \
  --model-id default \
  --questions-file tests/regression/questions.txt \
  --output-json tests/regression/classifier_regression_baseline.json
```

Defaults match the paths above (you can omit flags for local port 8000).

Optional (if an evaluation dataset exists):

```bash
python tests/regression/capture_baseline.py --include-evaluation
```

### Step 2: Check regression after changes

```bash
cd classifier-service
python tests/regression/check_baseline.py \
  --classifier-base-url http://localhost:8000 \
  --baseline-json tests/regression/classifier_regression_baseline.json
```

Exit code: `0` if match, `1` if differences.

### Automated harness tests (no HTTP)

```bash
cd classifier-service
pytest tests/regression/test_baseline_lib.py -v
```

See also `classifier-service/tests/regression/README.md`.
