package com.uniovi.rag.domain.runtime.engine;

/**
 * How {@link ExecutionContext} was constructed for a single orchestrated turn.
 */
public enum RuntimeOperationKind {
    CHAT_MESSAGE,
    STATELESS_HTTP,
    LAB_PROCESS
}
