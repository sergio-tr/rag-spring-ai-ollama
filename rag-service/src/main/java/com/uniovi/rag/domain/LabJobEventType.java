package com.uniovi.rag.domain;

/** Canonical Lab job lifecycle events emitted over SSE and stored for resume. */
public enum LabJobEventType {
    ACCEPTED,
    STARTED,
    /** Campaign job accepted; payload includes {@code campaignId} and {@code totalItems}. */
    CAMPAIGN_ACCEPTED,
    /** Execution plan materialized; payload includes {@code groups} (axis → runId). */
    CAMPAIGN_PLANNED,
    CAMPAIGN_STARTED,
    RUN_STARTED,
    ITEM_STARTED,
    ITEM_COMPLETED,
    RUN_COMPLETED,
    CAMPAIGN_COMPLETED,
    /** RAG benchmark accepted; payload may include {@code corpusId} and {@code datasetId}. */
    RAG_EVALUATION_ACCEPTED,
    /** Index snapshot build started for a RAG preset group. */
    SNAPSHOT_PREPARATION_STARTED,
    /** Index snapshot build finished for a RAG preset group. */
    SNAPSHOT_PREPARATION_COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED,
    /** @deprecated Use granular lifecycle events; kept for stored log backward compatibility. */
    PROGRESS,
    /** @deprecated Use {@link #RUN_COMPLETED}; kept for stored log backward compatibility. */
    COMPLETED,
    /** Controller-only keepalive; not persisted in the event log. */
    HEARTBEAT
}
