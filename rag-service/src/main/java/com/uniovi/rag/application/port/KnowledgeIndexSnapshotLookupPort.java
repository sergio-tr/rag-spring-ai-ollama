package com.uniovi.rag.application.port;

import com.uniovi.rag.application.service.knowledge.IndexSnapshotEmbeddingLookup;
import java.util.List;
import java.util.UUID;

/**
 * Read-only lookup of knowledge index snapshots for embedding alignment (implementation uses persistence).
 */
public interface KnowledgeIndexSnapshotLookupPort {

    List<IndexSnapshotEmbeddingLookup> findCorpusSnapshots(UUID corpusId);

    List<IndexSnapshotEmbeddingLookup> findProjectSnapshots(UUID projectId);
}
