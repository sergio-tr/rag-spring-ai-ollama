# Regression tests (classifier-service)

Esta carpeta contiene **harnesses y datos de regresión manual** frente a una instancia HTTP en ejecución. No sustituyen a los **tests unitarios** (`tests/unit/`) ni a los tests de API (`tests/test_api.py`).

| Archivo | Rol |
|--------|-----|
| `questions.txt` | Preguntas fijas para capturar/comparar baseline. |
| `baseline_lib.py` | Utilidades compartidas (lectura de preguntas, comparación); cubierto por pytest en `test_baseline_lib.py`. |
| `capture_baseline.py` | Genera `docs/classifier_regression_baseline.json` llamando a `POST /classify` (y opcionalmente `/evaluate`). |
| `check_baseline.py` | Compara el comportamiento actual contra ese JSON. |

## Uso (desde `classifier-service/`)

```bash
# Con el servicio levantado (uvicorn o Docker)
python tests/regression/capture_baseline.py
python tests/regression/check_baseline.py
```

## Tests unitarios del servicio

- `tests/unit/` — lógica aislada (model loader, inference, config, etc.).
- `tests/test_api.py` — API con `TestClient` (sin red).
- `tests/regression/test_baseline_lib.py` — solo la lógica pura del harness de regresión (sin HTTP).
