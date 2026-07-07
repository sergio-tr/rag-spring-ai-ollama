package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional access to {@code knowledge_index_snapshot.index_profile_jsonb} so compatibility checks
 * do not rely on detached {@link KnowledgeIndexSnapshotEntity} proxies (e.g. from {@code EvaluationRunEntity#indexSnapshot}).
 */
@Service
public class KnowledgeIndexSnapshotProfileAccess {

    private final KnowledgeIndexSnapshotRepository repository;

    public KnowledgeIndexSnapshotProfileAccess(KnowledgeIndexSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadProfileJsonb(UUID snapshotId) {
        if (snapshotId == null) {
            return Map.of();
        }
        return repository
                .findById(snapshotId)
                .map(s -> s.getIndexProfileJsonb() != null ? s.getIndexProfileJsonb() : Map.<String, Object>of())
                .orElse(Map.of());
    }

    /**
     * Resolves profile JSON for a snapshot reference. Always reloads by id when present so callers
     * outside a Hibernate session never touch lazy entity state.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> resolveProfileJsonb(KnowledgeIndexSnapshotEntity snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            return Map.of();
        }
        return repository
                .findById(snapshot.getId())
                .map(s -> s.getIndexProfileJsonb() != null ? s.getIndexProfileJsonb() : Map.<String, Object>of())
                .orElse(Map.of());
    }
}
