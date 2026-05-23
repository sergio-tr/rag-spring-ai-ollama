package com.uniovi.rag.domain.knowledge;

/**
 * Logical owner of a {@code knowledge_index_snapshot} row (project chat scope vs Lab evaluation corpus).
 */
public enum KnowledgeSnapshotOwnerType {
    PROJECT,
    EVALUATION_CORPUS
}
