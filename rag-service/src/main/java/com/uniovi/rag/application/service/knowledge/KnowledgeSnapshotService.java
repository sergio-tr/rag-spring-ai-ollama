package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Snapshot lifecycle: BUILDING / ACTIVE / SUPERSEDED / FAILED and vector_store cleanup by snapshot id.
 */
@Service
public class KnowledgeSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSnapshotService.class);

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
            String resolvedConfigHash,
            Map<String, Object> indexProfileJsonb,
            String indexProfileHash) {
        return createBuildingSnapshot(
                project,
                conversation,
                scopeType,
                null,
                null,
                signatureHash,
                resolvedConfigSnapshotId,
                resolvedConfigHash,
                indexProfileJsonb,
                indexProfileHash);
    }

    @Transactional
    public KnowledgeIndexSnapshotEntity createBuildingSnapshot(
            ProjectEntity project,
            ConversationEntity conversation,
            KnowledgeSnapshotScopeType scopeType,
            KnowledgeSnapshotOwnerType ownerType,
            UUID ownerId,
            String signatureHash,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash,
            Map<String, Object> indexProfileJsonb,
            String indexProfileHash) {
        if (resolvedConfigSnapshotId == null || resolvedConfigHash == null || resolvedConfigHash.isBlank()) {
            throw new IllegalArgumentException("resolved_config_snapshot linkage required for knowledge_index_snapshot");
        }
        Instant now = Instant.now();
        KnowledgeIndexSnapshotEntity e = new KnowledgeIndexSnapshotEntity();
        e.setSignatureHash(signatureHash);
        e.setScopeType(scopeType);
        e.setOwnerType(ownerType);
        e.setOwnerId(ownerId);
        e.setProject(project);
        e.setConversation(conversation);
        e.setStatus(IndexSnapshotStatus.BUILDING);
        e.setResolvedConfigSnapshotId(resolvedConfigSnapshotId);
        e.setResolvedConfigHash(resolvedConfigHash);
        e.setIndexProfileJsonb(indexProfileJsonb != null ? indexProfileJsonb : Map.of());
        e.setIndexProfileHash(indexProfileHash);
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
        // Self-heal against stale state or concurrent ingests:
        // the DB may temporarily contain >1 ACTIVE snapshot for the same scope.
        // Before activating the new snapshot, we mark all other ACTIVE rows as SUPERSEDED.
        supersedeAllOtherActiveSnapshotsInScope(snapshot);
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
            if (d == null || d.getId() == null) {
                continue;
            }
            UUID documentId = d.getId();
            KnowledgeSnapshotDocumentEntity link = new KnowledgeSnapshotDocumentEntity();
            KnowledgeSnapshotDocumentPk pk = new KnowledgeSnapshotDocumentPk(snapshot.getId(), documentId);
            link.setId(pk);
            link.setSnapshot(snapshot);
            link.setDocument(knowledgeDocumentRepository.getReferenceById(documentId));
            snapshotDocumentRepository.save(link);
            KnowledgeDocumentEntity managed = knowledgeDocumentRepository.getReferenceById(documentId);
            managed.setCurrentIndexSnapshot(snapshot);
            knowledgeDocumentRepository.save(managed);
        }
    }

    private void supersedeAllOtherActiveSnapshotsInScope(KnowledgeIndexSnapshotEntity snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.getOwnerType() == KnowledgeSnapshotOwnerType.EVALUATION_CORPUS && snapshot.getOwnerId() != null) {
            supersedeOtherActiveCorpusSnapshots(snapshot);
            return;
        }
        Instant now = Instant.now();
        if (snapshot.getScopeType() == KnowledgeSnapshotScopeType.PROJECT) {
            var pid = snapshot.getProject() != null ? snapshot.getProject().getId() : null;
            if (pid == null) return;
            var actives =
                    snapshotRepository.findActiveProjectSnapshots(
                            pid, KnowledgeSnapshotScopeType.PROJECT, IndexSnapshotStatus.ACTIVE);
            for (KnowledgeIndexSnapshotEntity a : actives) {
                if (a.getId() != null && !a.getId().equals(snapshot.getId())) {
                    a.setStatus(IndexSnapshotStatus.SUPERSEDED);
                    a.setUpdatedAt(now);
                    snapshotRepository.save(a);
                }
            }
        } else if (snapshot.getScopeType() == KnowledgeSnapshotScopeType.CONVERSATION
                && snapshot.getConversation() != null) {
            var cid = snapshot.getConversation().getId();
            var actives =
                    snapshotRepository.findActiveConversationSnapshots(
                            cid, KnowledgeSnapshotScopeType.CONVERSATION, IndexSnapshotStatus.ACTIVE);
            for (KnowledgeIndexSnapshotEntity a : actives) {
                if (a.getId() != null && !a.getId().equals(snapshot.getId())) {
                    a.setStatus(IndexSnapshotStatus.SUPERSEDED);
                    a.setUpdatedAt(now);
                    snapshotRepository.save(a);
                }
            }
        }
    }

    private void supersedeOtherActiveCorpusSnapshots(KnowledgeIndexSnapshotEntity snapshot) {
        Instant now = Instant.now();
        List<KnowledgeIndexSnapshotEntity> actives =
                snapshotRepository.findActiveByOwner(
                        KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                        snapshot.getOwnerId(),
                        IndexSnapshotStatus.ACTIVE);
        for (KnowledgeIndexSnapshotEntity a : actives) {
            if (a.getId() != null
                    && !a.getId().equals(snapshot.getId())
                    && Objects.equals(a.getIndexProfileHash(), snapshot.getIndexProfileHash())) {
                a.setStatus(IndexSnapshotStatus.SUPERSEDED);
                a.setUpdatedAt(now);
                snapshotRepository.save(a);
            }
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

    public List<KnowledgeIndexSnapshotEntity> findCorpusSnapshots(UUID corpusId) {
        if (corpusId == null) {
            return List.of();
        }
        return snapshotRepository.findByOwnerOrderByCreatedAtDesc(
                KnowledgeSnapshotOwnerType.EVALUATION_CORPUS, corpusId);
    }

    public Optional<KnowledgeIndexSnapshotEntity> findActiveCorpusSnapshot(UUID corpusId) {
        List<KnowledgeIndexSnapshotEntity> rows =
                snapshotRepository.findActiveByOwner(
                        KnowledgeSnapshotOwnerType.EVALUATION_CORPUS, corpusId, IndexSnapshotStatus.ACTIVE);
        return rows.stream().findFirst();
    }

    public Optional<KnowledgeIndexSnapshotEntity> findCompatibleCorpusSnapshot(
            UUID corpusId, Predicate<KnowledgeIndexSnapshotEntity> compatibilityPredicate) {
        if (compatibilityPredicate == null || corpusId == null) {
            return Optional.empty();
        }
        return findCorpusSnapshots(corpusId).stream().filter(compatibilityPredicate).findFirst();
    }

    public Optional<KnowledgeIndexSnapshotEntity> findActiveProjectSnapshot(UUID projectId) {
        List<KnowledgeIndexSnapshotEntity> rows = snapshotRepository.findActiveProjectSnapshots(
                projectId, KnowledgeSnapshotScopeType.PROJECT, IndexSnapshotStatus.ACTIVE);
        if (rows.size() > 1) {
            log.warn("Multiple ACTIVE project snapshots found for project {} (count={}); using most recent", projectId, rows.size());
        }
        return rows.stream().findFirst();
    }

    public List<KnowledgeIndexSnapshotEntity> findProjectSnapshots(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        return snapshotRepository.findByProjectAndScopeProjectOrderByCreatedAtDesc(
                projectId, KnowledgeSnapshotScopeType.PROJECT);
    }

    public Optional<KnowledgeIndexSnapshotEntity> findCompatibleProjectSnapshot(
            UUID projectId,
            Predicate<KnowledgeIndexSnapshotEntity> compatibilityPredicate) {
        if (compatibilityPredicate == null) {
            return Optional.empty();
        }
        return findProjectSnapshots(projectId).stream().filter(compatibilityPredicate).findFirst();
    }

    public Optional<KnowledgeIndexSnapshotEntity> findActiveConversationSnapshot(UUID conversationId) {
        List<KnowledgeIndexSnapshotEntity> rows = snapshotRepository.findActiveConversationSnapshots(
                conversationId, KnowledgeSnapshotScopeType.CONVERSATION, IndexSnapshotStatus.ACTIVE);
        if (rows.size() > 1) {
            log.warn(
                    "Multiple ACTIVE conversation snapshots found for conversation {} (count={}); using most recent",
                    conversationId,
                    rows.size());
        }
        return rows.stream().findFirst();
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
