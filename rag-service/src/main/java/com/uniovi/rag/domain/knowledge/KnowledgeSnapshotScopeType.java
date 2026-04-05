package com.uniovi.rag.domain.knowledge;

/**
 * Granularity of a logical index snapshot row (project-wide vs conversation-scoped overlay).
 */
public enum KnowledgeSnapshotScopeType {
    PROJECT,
    CONVERSATION
}
