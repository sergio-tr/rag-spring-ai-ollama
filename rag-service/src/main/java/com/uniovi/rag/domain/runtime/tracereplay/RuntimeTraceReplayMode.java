package com.uniovi.rag.domain.runtime.tracereplay;

/**
 * How a replay request selects the persisted orchestrated trace (P18).
 */
public enum RuntimeTraceReplayMode {
    BY_TRACE_ID,
    BY_MESSAGE_ID
}
