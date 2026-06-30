# Classifier status freeze

**Effective:** 2026-06-29  
**Gate:** Classifier Retrain and Failure Analysis (`classifier-retrain-failure-analysis-20250629`) — **CONDITIONAL_PASS**  
**Evidence:** evaluation evidence package `classifier-status-freeze-20250629` (JSON/CSV exports; internal agent capture may use a local working directory).

---

## Summary decision

The **deployable Keras default classifier** (`classifier-service/models/default/`) is **not** accepted as a final production routing component. It remains a **documented experimental component** with known poor held-out performance after hygienic train-only retraining.

**Embeddings and LLMs may proceed** in classifier-independent setups. **RAG preset comparisons must not** treat the current Keras default as an optimized or final classifier.

---

## Measured macro-F1 (held-out `evaluation_dataset.xlsx`, 60 rows)

| Model / candidate | Train source | Uses eval for train? | Macro-F1 | Status |
| --- | --- | --- | ---: | --- |
| C0 — legacy Keras (pre-hygienic retrain) | train + eval (leaked) | **Yes** | 0.369 | **Invalid** — inflated by leakage |
| **C1 — deployable Keras default** | clean train only (46 rows) | No | **0.013** | **Not production-quality** |
| C2 — TF-IDF word + LinearSVC | clean train only | No | 0.661 | Offline analysis only |
| **C3 — TF-IDF char_wb + LinearSVC** | clean train only | No | **0.797** | Best offline; **not served** |
| C4 — TF-IDF char_wb + LogReg balanced | clean train only | No | 0.776 | Offline analysis only |

Accuracy for C1: **0.083**. C1 collapses toward `COUNT_AND_EXPLAIN` on most eval rows.

---

## Why embeddings and LLMs are not blocked

| Layer | Classifier involvement |
| --- | --- |
| Embedding retrieval evaluation | **None** — retrieval metrics do not depend on query-type routing |
| LLM oracle-context evaluation | **None** — context is provided; routing is bypassed |
| RAG preset evaluation | **Conditional** — classifier may run but must be **disabled, frozen, deterministic-only, or explicitly reported** as weak |

Classifier quality does **not** invalidate embedding or LLM layer studies when those layers are evaluated independently.

---

## Why the classifier is not final

1. **Train/eval leakage closed** (11 → 0 overlaps) — methodology is now defensible, but the legacy model was trained on leaked data.
2. **Hygienic Keras retrain regressed** — macro-F1 0.369 → 0.013 when eval was removed from training.
3. **Train set is small and imbalanced** — 46 rows, 12 classes, 2–8 examples per class.
4. **Label boundaries are ambiguous** — especially `COUNT_DOCUMENTS` vs `COUNT_AND_EXPLAIN`, `GET_DURATION` overprediction.
5. **Offline sklearn (C3) reaches 0.797** — shows the bottleneck is **architecture + data scale**, not the eval set itself.
6. **Dataset expansion planned but not applied** — `proposed_train_expansion.json` (+50 train rows to reach ~8/class).

---

## Campaign constraints

### Allowed without classifier acceptance

- Embedding retrieval evaluation (classifier not involved)
- LLM oracle-context evaluation (classifier not involved)

### RAG preset evaluation — required handling

One of:

- Classifier **disabled** or routing **bypassed** for the campaign row
- **Deterministic-only** routing (`ClassifierDeterministicResolver` high-precision rules)
- **Frozen** current Keras default with explicit reporting: *weak classifier, macro-F1 0.013, not optimized*
- Fixed classifier id documented in export manifest

### Not allowed

- Claiming RAG improvements are due to classifier optimization while using C1
- Running a **classifier comparison campaign** until an accepted model exists (macro-F1 ≥ 0.65 on held-out eval, or documented alternative)
- Attributing final RAG quality to classifier without rerunning classifier campaign on accepted model

**Rule:** No final RAG claim may attribute improvements to classifier until a classifier campaign is rerun with an accepted model.

---

## Next classifier options (post-freeze)

| Option | Description | When to choose |
| --- | --- | --- |
| **A. Dataset expansion + Keras retry** | Apply `proposed_train_expansion.json` (+50 rows), retrain Keras train-only, target macro-F1 ≥ 0.65 | Prefer if staying on current serving stack |
| **B. sklearn C3/C4 serving** | Serve TF-IDF char_wb + LinearSVC or LogReg as `models/default` | Prefer if fastest path to acceptable macro-F1 |
| **C. Deterministic + ML hybrid** | High-precision rules first, ML fallback for ambiguous queries | Prefer for routing safety; document ambiguity policy |

---

## Hygiene status (closed)

| Check | Status |
| --- | --- |
| Train/eval normalized overlap | **0** (audit PASS) |
| `retrain_default_model.py` trains on eval | **No** — guards enforce train-only |
| Regression baseline non-empty | **Yes** — post-retrain capture |
| classifier-service pytest | **181/181 PASS** |
| Backend classifier contract tests | **19/19 PASS** |

---

*Documentation only — no campaigns, retrain, or dataset changes in this freeze gate.*
