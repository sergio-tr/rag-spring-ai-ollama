package com.uniovi.rag.service.evaluation.preset;

/**
 * Execution bucket for thesis P0–P14 preset sweeps (index-aware Lab planning).
 */
public enum LabPresetRunGroupKey {
    /** Presets with {@link ExperimentalPresetCanonicalCatalog.RequiredMaterialization#NONE} only (no remaining thesis presets here after P0/P1 index alignment). */
    NO_INDEX,
    /** P2 — document-level dense retrieval. */
    DOCUMENT_LEVEL,
    /** P3 — chunk-level retrieval (no mandatory index metadata). */
    CHUNK_LEVEL,
    /** P4–P7 — chunk-level + metadata index support. */
    CHUNK_LEVEL_METADATA,
    /** P8–P12 — hybrid materialization + metadata. */
    HYBRID_METADATA,
    /** P13, P14 — not runnable in single-turn Lab harness. */
    MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN
}
