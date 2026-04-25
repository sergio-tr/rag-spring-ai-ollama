package com.uniovi.rag.domain.runtime.memory;

/**
 * Terminal memory outcomes for one orchestrated turn (P12).
 */
public enum ConversationMemoryOutcome {
    DISABLED_BY_CONFIG,
    NO_CONVERSATION_SCOPE,
    NO_HISTORY_AVAILABLE,
    SINGLE_TURN_NO_MEMORY_NEEDED,
    MEMORY_APPLIED,
    CONDENSE_FAILED_FALLBACK
}

