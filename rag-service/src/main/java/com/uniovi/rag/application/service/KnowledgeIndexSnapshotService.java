package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Logical index snapshots, including synthetic LEGACY rows per project during transition.
 */
@Service
public class KnowledgeIndexSnapshotService {

    public static final String LEGACY_SIGNATURE_PREFIX = "LEGACY:";

    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    public KnowledgeIndexSnapshotService(KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository) {
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
    }

    public static String legacySignatureForProject(UUID projectId) {
        return LEGACY_SIGNATURE_PREFIX + projectId;
    }

    @Transactional
    public KnowledgeIndexSnapshotEntity ensureLegacySnapshotForProject(ProjectEntity project) {
        UUID pid = project.getId();
        String sig = legacySignatureForProject(pid);
        return knowledgeIndexSnapshotRepository
                .findByProject_IdAndSignatureHashAndStatus(pid, sig, IndexSnapshotStatus.ACTIVE)
                .orElseGet(() -> createActiveProjectSnapshot(project, sig));
    }

    private KnowledgeIndexSnapshotEntity createActiveProjectSnapshot(ProjectEntity project, String signatureHash) {
        Instant now = Instant.now();
        KnowledgeIndexSnapshotEntity e = new KnowledgeIndexSnapshotEntity();
        e.setSignatureHash(signatureHash);
        e.setScopeType(KnowledgeSnapshotScopeType.PROJECT);
        e.setProject(project);
        e.setStatus(IndexSnapshotStatus.ACTIVE);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return knowledgeIndexSnapshotRepository.save(e);
    }
}
