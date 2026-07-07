# Regression tests (classifier-service)

This folder contains **manual regression harnesses and data** against a running HTTP instance. It does not replace **unit tests** (`tests/unit/`) or API tests (`tests/test_api.py`).

| File | Role |
| --- | --- |
| `questions.txt` | Fixed questions to capture/compare baseline. |
| `baseline_lib.py` | Shared utilities (read questions, compare); covered by pytest in `test_baseline_lib.py`. |
| `capture_baseline.py` | Writes `classifier_regression_baseline.json` in this folder via `POST /classify` (and optionally `/evaluate`). |
| `check_baseline.py` | Compares current behavior against that JSON. |

## Usage (from `classifier-service/`)

```bash
# With the service running (uvicorn or Docker)
python tests/regression/capture_baseline.py
python tests/regression/check_baseline.py
```

## Other tests in the service

- `tests/unit/` - isolated logic (model loader, inference, config, etc.).
- `tests/test_api.py` - API with `TestClient` (no network).
- `tests/regression/test_baseline_lib.py` - pure harness logic only (no HTTP).
