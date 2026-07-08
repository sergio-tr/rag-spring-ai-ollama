# Classifier status freeze

> **Superseded 2026-07-02 (closure 2026-07-06):** Default classifier promoted to sklearn **C3** (`LinearSVC`, char_wb TF-IDF). Held-out macro-F1 **0.9663** on `evaluation_dataset.xlsx` (60 rows). See `classifier-service/models/default/metadata.json`. **No retraining required.**

---

## Current accepted state

| Item | Current value |
|------|---------------|
| Deployed default | sklearn C3, `models/default/model.joblib` |
| Algorithm | `TfidfVectorizer(analyzer="char_wb", ngram_range=(3,5))` + **LinearSVC** |
| Train dataset | `basic_dataset_qa_clasificacion_final.xlsx` (**213 rows**) |
| Held-out eval | `evaluation_dataset.xlsx` (60 rows, 0 train overlap) |
| Gold probe | `gold-subset-v1.json` (18 rows, report-only) |
| Primary metrics | accuracy **0.9667**, macro-F1 **0.9663**, macro precision **0.9722**, macro recall **0.9667** (58/60) |
| Gold subset probe | accuracy **0.9444** (17/18) |
| Production routing | **Accepted** for default RAG path |
| Retraining | **Not required** |

### Held-out misclassifications (2)

| True label | Predicted | Count |
|------------|-----------|------:|
| `COUNT_AND_EXPLAIN` | `FILTER_AND_LIST` | 1 |
| `SUMMARIZE_MEETING` | `EXTRACT_ENTITIES` | 1 |

### Custom Lab training vs default

| Path | Vectorizer | Estimator |
|------|------------|-----------|
| Default C3 (`train_sklearn_classifier.py --variant C3`) | char_wb TF-IDF (3–5) | **LinearSVC** |
| HTTP `POST /train` (`SklearnTrainingPipeline`) | char_wb TF-IDF (3–5) | **LogisticRegression** (balanced) |

Custom Lab models are registered under UUID subdirs; they do not overwrite the protected `default` id.

---

## Historical context (2026-06-29 freeze — Keras superseded)

The **Keras default classifier** (C1) was **not** accepted as a production routing component after hygienic train-only retraining (macro-F1 **0.013** on 46-row train set). That path is **superseded**; `modelType: sklearn` in `metadata.json` is authoritative. Legacy Keras training code remains for optional Lab/GPU use only (`requirements-gpu.txt`); it is **not** the final runtime for the shipped default.

| Model / candidate (historical) | Train source | Macro-F1 (held-out) | Status |
| --- | --- | ---: | --- |
| C0 — legacy Keras (pre-hygienic) | train + eval (leaked) | 0.369 | Invalid — leakage |
| C1 — Keras train-only | 46 rows | **0.013** | Superseded — not served |
| C3 — sklearn char_wb + LinearSVC | 213 rows (final) | **0.9663** | **Deployed default** |

---

## Campaign constraints (updated)

### Allowed

- Embedding retrieval evaluation (classifier-independent)
- LLM oracle-context evaluation (classifier-independent)
- RAG preset evaluation with the **accepted sklearn C3 default** (document `classifierModelId=default` in exports)

### RAG preset rows — when not using default

- Classifier **disabled** or routing **bypassed**
- **Deterministic-only** routing (`ClassifierDeterministicResolver`)
- Fixed custom classifier id documented in export manifest (Lab `/train` models use LogisticRegression — metrics not interchangeable with default)

### Not allowed

- Treating legacy Keras C1 as an optimized or final classifier
- Attributing RAG improvements to classifier optimization without documenting the classifier version and held-out metrics

---

## Hygiene status

| Check | Status |
| --- | --- |
| Train/eval normalized overlap | **0** |
| `retrain_default_model.py` trains on eval | **No** — guards enforce train-only |
| Default `id=default` protected from overwrite | **Yes** |
| classifier-service pytest | Green (see closure evidence) |

---

*Documentation only — classifier section closed 2026-07-06; no default retrain or overwrite.*
