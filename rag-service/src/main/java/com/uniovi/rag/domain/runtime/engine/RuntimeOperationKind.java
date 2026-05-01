package com.uniovi.rag.domain.runtime.engine;

/**
 * How {@link ExecutionContext} was constructed for a single orchestrated turn.
 */
public enum RuntimeOperationKind {
    CHAT_MESSAGE,
    LEGACY_HTTP,
    LAB_PROCESS
}
