package com.uniovi.rag.application.service.knowledge;

import java.util.Map;
import java.util.UUID;

/**
 * Minimal index-snapshot view for embedding-model alignment in Lab campaigns (no JPA types in application callers).
 */
public record IndexSnapshotEmbeddingLookup(UUID id, Map<String, Object> indexProfileJsonb) {}
