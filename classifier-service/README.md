# Classifier Service

Query-type classification service for the RAG backend. It exposes an HTTP API used by the RAG service to classify user questions (e.g. `COUNT_DOCUMENTS`, `SUMMARIZE_MEETING`).

**Target architecture (frozen model):** [Classifier System](../docs/architecture/target-architecture.md).

## Architecture

- **Domain models** (`app/models/`): `ClassificationResult`, `ModelMetadata`, `TrainingResult`, `ErrorDetail` — value objects for responses and errors.
- **Exceptions** (`app/exceptions.py`): `ValidationError` (400), `ModelNotFoundError` (404), `ClassificationError` (503), `TrainingError` (500).
- **Inference** (`app/inference/`): `ModelLoader` (loads and caches Keras model + labels), `InferenceEngine` (runs prediction).
- **Registry** (`app/registry/`): `ModelRegistry` (list models, resolve paths, register trained models).
- **Training** (`app/training/`): `TrainingPipeline` (Excel → train → save → register).
- **Evaluation** (`app/evaluation/`): `EvaluationPipeline` (metrics + classification report and confusion matrix PNGs), `EvaluationResult`.
- **Services** (`app/services/`): `ClassificationService`, `ModelRegistryService`, `TrainingService`, `EvaluationService` — orchestration; routes call these only.
- **Routes** (`app/routes.py`): Thin HTTP layer; delegates to services and maps exceptions to structured error responses.

All code comments are in English.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/health` | Service status; includes whether default model is loaded. |
| GET | `/models` | List available models (default first, then trained). |
| POST | `/classify` | Body `{"query": "...", "modelId": "default"}` (modelId optional). Returns `{"queryType": "COUNT_DOCUMENTS"}` etc. All JSON in **camelCase**. |
| POST | `/train` | Multipart: Excel (Question, QueryType) + `model_name` (tag), optional `labels` (JSON array) or `labels_file` (.txt). Optional `epochs`, `batch_size`. Response: `modelId`, `name`, `metrics` (camelCase). |
| POST | `/evaluate` | Query params `modelId`, `includeImages`; optional file. Returns `modelId`, `metrics` (classificationReport, accuracy, macroAvg, confusionMatrix, classNames), optional base64 images (camelCase). |
| GET | `/evaluate/{model_id}/report.png` | Classification report heatmap PNG for the model (path segment = model id). |
| GET | `/evaluate/{model_id}/confusion.png` | Confusion matrix heatmap PNG for the model. |

- **Use classifier by tag:** send `modelId` in `POST /classify` (query param or body); use **camelCase** in all request/response JSON for interoperability with Java/JavaScript.
- **Train with fixed labels:** send `labels` as a JSON string array (e.g. `["COUNT_DOCUMENTS","SUMMARIZE_MEETING"]`) or upload a `labels_file` (one label per line, like `query_type_labels.txt`). Class order is preserved.
- **Evaluation:** `POST /evaluate` returns full metrics (classification_report, accuracy, macro_avg, confusion_matrix, class_names) and optionally `classificationReportImageBase64` and `confusionMatrixImageBase64` for the webapp (display or download). The GET image endpoints return the PNGs directly for use in `<img src="...">` or download links.

Error responses use a consistent shape: `{"code": "...", "message": "...", "details": {...}}` (optional).

## Datasets

Default datasets are under `data/`:

- `basic_dataset_qa_clasificacion.xlsx` — training dataset (Question, QueryType).
- `evaluation_dataset.xlsx` — evaluation dataset.

These were moved from `rag-service/src/main/resources/python/` and are the single source of truth here.

## Run locally

```bash
pip install -r requirements.txt
# Ensure models/default/model.keras and models/default/labels.txt exist
uvicorn main:app --host 0.0.0.0 --port 8000
```

Or with Docker (from repo root):

```bash
docker compose -f docker/docker-compose.yml up -d classifier-service
```

The RAG backend is configured via `RAG_CLASSIFIER_SERVICE_URL` (default `http://localhost:8000`; in Docker `http://classifier-service:8000`).

## Environment

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | 8000 | Server port. |
| `UVICORN_HOST` | `127.0.0.1` | Bind address for `python main.py` (local). Set to `0.0.0.0` when the process must listen on all interfaces (Docker/Compose already pass `--host 0.0.0.0` to uvicorn). |
| `MODELS_DIR` | models | Directory for default and trained models. |
| `DATA_DIR` | data | Directory for default datasets. |
| `DEFAULT_MODEL_ID` | default | Model id used when none is specified. |
| `MODEL_PATH` | `{MODELS_DIR}/default/model.keras` | Default model file. |
| `LABELS_PATH` | `{MODELS_DIR}/default/labels.txt` | Default labels file. |

## Tests

- **`tests/unit/`** — unit tests (model, inference, config, isolated services).
- **`tests/test_api.py`** — API tests with `TestClient` (no external network).
- **`tests/regression/`** — manual regression against a running HTTP service (`capture_baseline.py` / `check_baseline.py`) and pytest for harness logic (`test_baseline_lib.py`). See `tests/regression/README.md` and **Manual regression testing** below.

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
