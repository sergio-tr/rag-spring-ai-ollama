package com.uniovi.rag.application.service.evaluation.preset;

/**
 * Execution bucket for product P0–P14 preset sweeps (index-aware Lab planning).
 */
public enum LabPresetRunGroupKey {
    /** P0 — direct LLM without corpus assembly or index binding. */
    DIRECT_LLM,
    /** P1 — full corpus prompt assembled from evaluation corpus snapshot rows. */
    NO_INDEX,
    /** P2 — document-level dense retrieval. */
    DOCUMENT_LEVEL,
    /** P3 — chunk-level retrieval (no mandatory index metadata). */
    CHUNK_LEVEL,
    /** P4–P7 — chunk-level + metadata index support. */
    CHUNK_LEVEL_METADATA,
    /** P8–P10 — hybrid materialization + metadata. */
    HYBRID_METADATA,
    /** P11–P14 — not runnable in single-turn Lab harness. */
    MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN
}
