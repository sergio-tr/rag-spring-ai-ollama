# Empirical research and experimentation

**Purpose:** Normative, incremental research programme for the project: hypotheses, variables, datasets, baselines, ablations, metrics, traceability, and reproducibility. This folder is the **canonical** location for methodology and run evidence **indexes**; large binary exports stay in operator-controlled storage with paths recorded in run sheets.

**Normative references (repository):**

- Lab benchmarks and exports: [`rag-service/README.md`](../../rag-service/README.md) (Lab benchmarks section).
- Quality gates and mocks policy: [`docs/quality/README.md`](../quality/README.md), [`docs/testing/external-test-harness.md`](../testing/external-test-harness.md).
- Minimum scope: [`docs/overview/minimum-scope.md`](../overview/minimum-scope.md).
- Unified product/Lab engine: [`docs/adr/0009-unified-product-and-lab-execution-engine.md`](../adr/0009-unified-product-and-lab-execution-engine.md).
- Spring AI / pipeline context (supporting, not duplicate of module READMEs): [`docs/ai/README.md`](../ai/README.md).

## Documents in this folder

| Document | Role |
| --- | --- |
| [`inventory-repository-state.md`](inventory-repository-state.md) | Verified snapshot: already implemented / pending / to verify (dated + git SHA). |
| [`protocol-reproducibility.md`](protocol-reproducibility.md) | Freeze windows, versioning anchors, dataset manifests, artefact registration. |
| [`experimental-design-matrix.md`](experimental-design-matrix.md) | Hypotheses, IV/DV, baselines, ablations, metrics, acceptance and rejection rules. |
| [`run-record-template.md`](run-record-template.md) | Blank run sheet (copy per execution). |
| [`wave-pilot.md`](wave-pilot.md) | Pilot wave: objectives, run records, reproducibility lessons. |
| [`wave-comparative.md`](wave-comparative.md) | Comparative wave: frozen code window, mandatory baselines, ablation matrix execution log. |
| [`final-evaluation-synthesis.md`](final-evaluation-synthesis.md) | Closing package: synthesis, validity threats, metric limits, artefact index. |
| [`evaluation-protocol.md`](evaluation-protocol.md) | Layered evaluation protocol: datasets, metrics, exports, decision criteria (pre-campaign gate). |
| [`classifier-status-freeze.md`](classifier-status-freeze.md) | Classifier accepted (sklearn C3, macro-F1 0.9663); historical Keras freeze superseded 2026-07-02. |

**Documentation allowlist for edits:** this tree (`docs/research/**`), plus `docs/architecture/**` (excluding denylisted files), `docs/ai/**`, `docs/adr/**`, and optional short pointer in `rag-service/README.md`. Do not edit `docs/architecture/DATA_MODEL.md` or `docs/architecture/configuration-resolution-model.md` unless a separate project decision overrides that denylist.
