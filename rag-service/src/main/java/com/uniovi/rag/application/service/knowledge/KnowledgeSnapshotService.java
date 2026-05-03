package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeSnapshotDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeSnapshotDocumentPk;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot lifecycle: BUILDING / ACTIVE / SUPERSEDED / FAILED and vector_store cleanup by snapshot id.
 */
@Service
public class KnowledgeSnapshotService {

    private final KnowledgeIndexSnapshotRepository snapshotRepository;
    private final KnowledgeSnapshotDocumentRepository snapshotDocumentRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSnapshotService(
            KnowledgeIndexSnapshotRepository snapshotRepository,
            KnowledgeSnapshotDocumentRepository snapshotDocumentRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            JdbcTemplate jdbcTemplate) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotDocumentRepository = snapshotDocumentRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public KnowledgeIndexSnapshotEntity createBuildingSnapshot(
            ProjectEntity project,
            ConversationEntity conversation,
            KnowledgeSnapshotScopeType scopeType,
            String signatureHash,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        if (resolvedConfigSnapshotId == null || resolvedConfigHash == null || resolvedConfigHash.isBlank()) {
            throw new IllegalArgumentException("resolved_config_snapshot linkage required for knowledge_index_snapshot");
        }
        Instant now = Instant.now();
        KnowledgeIndexSnapshotEntity e = new KnowledgeIndexSnapshotEntity();
        e.setSignatureHash(signatureHash);
        e.setScopeType(scopeType);
        e.setProject(project);
        e.setConversation(conversation);
        e.setStatus(IndexSnapshotStatus.BUILDING);
        e.setResolvedConfigSnapshotId(resolvedConfigSnapshotId);
        e.setResolvedConfigHash(resolvedConfigHash);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return snapshotRepository.save(e);
    }

    /**
     * Deletes vector rows tagged with this snapshot id (corpus pipeline ownership).
     */
    public void deleteVectorsForSnapshotId(UUID snapshotId) {
        if (snapshotId == null) {
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'indexSnapshotId' = ?",
                snapshotId.toString());
    }

    @Transactional
    public void activateSnapshot(
            KnowledgeIndexSnapshotEntity snapshot,
            List<KnowledgeDocumentEntity> documents,
            Optional<KnowledgeIndexSnapshotEntity> previousActive) {
        assertAtMostOneActiveSnapshotInScope(snapshot);
        Instant now = Instant.now();
        previousActive.ifPresent(
                prev -> {
                    prev.setStatus(IndexSnapshotStatus.SUPERSEDED);
                    prev.setUpdatedAt(now);
                    snapshotRepository.save(prev);
                });
        snapshot.setStatus(IndexSnapshotStatus.ACTIVE);
        snapshot.setUpdatedAt(now);
        snapshotRepository.save(snapshot);
        for (KnowledgeDocumentEntity d : documents) {
            KnowledgeSnapshotDocumentEntity link = new KnowledgeSnapshotDocumentEntity();
            KnowledgeSnapshotDocumentPk pk = new KnowledgeSnapshotDocumentPk(snapshot.getId(), d.getId());
            link.setId(pk);
            link.setSnapshot(snapshot);
            link.setDocument(d);
            snapshotDocumentRepository.save(link);
            d.setCurrentIndexSnapshot(snapshot);
            knowledgeDocumentRepository.save(d);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failSnapshotById(UUID snapshotId) {
        if (snapshotId == null) {
            return;
        }
        snapshotRepository
                .findById(snapshotId)
                .ifPresent(
                        snapshot -> {
                            snapshot.setStatus(IndexSnapshotStatus.FAILED);
                            snapshot.setUpdatedAt(Instant.now());
                            snapshotRepository.save(snapshot);
                        });
    }

    public Optional<KnowledgeIndexSnapshotEntity> findActiveProjectSnapshot(UUID projectId) {
        return snapshotRepository.findActiveProjectSnapshot(
                projectId, KnowledgeSnapshotScopeType.PROJECT, IndexSnapshotStatus.ACTIVE);
    }

    public Optional<KnowledgeIndexSnapshotEntity> findActiveConversationSnapshot(UUID conversationId) {
        return snapshotRepository.findActiveConversationSnapshot(
                conversationId, KnowledgeSnapshotScopeType.CONVERSATION, IndexSnapshotStatus.ACTIVE);
    }

    /**
     * §4a: at most one ACTIVE row per project (PROJECT scope) or per conversation (CONVERSATION scope).
     */
    private void assertAtMostOneActiveSnapshotInScope(KnowledgeIndexSnapshotEntity snapshot) {
        if (snapshot.getScopeType() == KnowledgeSnapshotScopeType.PROJECT) {
            long n =
                    snapshotRepository.countByProject_IdAndScopeTypeAndConversationIsNullAndStatus(
                            snapshot.getProject().getId(),
                            KnowledgeSnapshotScopeType.PROJECT,
                            IndexSnapshotStatus.ACTIVE);
            if (n > 1) {
                throw new IllegalStateException(
                        "ACTIVE snapshot uniqueness violated: multiple ACTIVE rows for project scope");
            }
        } else if (snapshot.getScopeType() == KnowledgeSnapshotScopeType.CONVERSATION
                && snapshot.getConversation() != null) {
            long n =
                    snapshotRepository.countByConversation_IdAndScopeTypeAndStatus(
                            snapshot.getConversation().getId(),
                            KnowledgeSnapshotScopeType.CONVERSATION,
                            IndexSnapshotStatus.ACTIVE);
            if (n > 1) {
                throw new IllegalStateException(
                        "ACTIVE snapshot uniqueness violated: multiple ACTIVE rows for conversation scope");
            }
        }
    }
}
