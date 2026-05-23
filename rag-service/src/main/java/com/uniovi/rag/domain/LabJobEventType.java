package com.uniovi.rag.domain;

/** Canonical Lab job lifecycle events emitted over SSE and stored for resume. */
public enum LabJobEventType {
    ACCEPTED,
    STARTED,
    PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    HEARTBEAT
}
