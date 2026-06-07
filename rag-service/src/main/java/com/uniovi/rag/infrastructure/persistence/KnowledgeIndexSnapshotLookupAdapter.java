package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.port.KnowledgeIndexSnapshotLookupPort;
import com.uniovi.rag.application.service.knowledge.IndexSnapshotEmbeddingLookup;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexSnapshotLookupAdapter implements KnowledgeIndexSnapshotLookupPort {

    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public KnowledgeIndexSnapshotLookupAdapter(KnowledgeSnapshotService knowledgeSnapshotService) {
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    @Override
    public List<IndexSnapshotEmbeddingLookup> findCorpusSnapshots(UUID corpusId) {
        return knowledgeSnapshotService.findCorpusSnapshots(corpusId).stream()
                .map(KnowledgeIndexSnapshotLookupAdapter::toLookup)
                .toList();
    }

    @Override
    public List<IndexSnapshotEmbeddingLookup> findProjectSnapshots(UUID projectId) {
        return knowledgeSnapshotService.findProjectSnapshots(projectId).stream()
                .map(KnowledgeIndexSnapshotLookupAdapter::toLookup)
                .toList();
    }

    private static IndexSnapshotEmbeddingLookup toLookup(KnowledgeIndexSnapshotEntity entity) {
        return new IndexSnapshotEmbeddingLookup(entity.getId(), entity.getIndexProfileJsonb());
    }
}
