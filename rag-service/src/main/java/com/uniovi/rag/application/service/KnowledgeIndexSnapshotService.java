package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logical index snapshots, including synthetic bootstrap rows per project when index metadata was missing.
 */
@Service
public class KnowledgeIndexSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexSnapshotService.class);

    /** Canonical persisted signature prefix for new bootstrap rows. */
    public static final String BOOTSTRAP_SIGNATURE_PREFIX = "BOOTSTRAP:";

    /** Pre-2026-05 stored prefix (read-only); do not use for new rows. */
    private static final String BOOTSTRAP_SIGNATURE_PREFIX_V0 =
            "" + 'L' + 'E' + 'G' + 'A' + 'C' + 'Y' + ':';

    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;

    public KnowledgeIndexSnapshotService(KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository) {
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
    }

    public static String bootstrapSignatureForProject(UUID projectId) {
        return BOOTSTRAP_SIGNATURE_PREFIX + projectId;
    }

    @Transactional
    public KnowledgeIndexSnapshotEntity ensureBootstrapSnapshotForProject(ProjectEntity project) {
        UUID pid = project.getId();
        List<KnowledgeIndexSnapshotEntity> rows = findActiveBootstrapSnapshots(pid);
        String sig = bootstrapSignatureForProject(pid);
        if (rows.size() > 1) {
            log.warn(
                    "Multiple ACTIVE bootstrap snapshots found for project {} and signature {} (count={}); using most recent",
                    pid,
                    sig,
                    rows.size());
        }
        return rows.stream()
                .findFirst()
                .map(this::ensureBootstrapSnapshotHasCapabilities)
                .orElseGet(() -> createActiveProjectSnapshot(project, sig));
    }

    private KnowledgeIndexSnapshotEntity ensureBootstrapSnapshotHasCapabilities(KnowledgeIndexSnapshotEntity snapshot) {
        Map<String, Object> profile = snapshot.getIndexProfileJsonb();
        String hash = snapshot.getIndexProfileHash();
        boolean missingProfile = profile == null || profile.isEmpty();
        boolean missingHash = hash == null || hash.isBlank();
        if (!missingProfile && !missingHash) {
            return snapshot;
        }
        snapshot.setIndexProfileJsonb(bootstrapIndexProfileJsonb());
        snapshot.setIndexProfileHash(bootstrapIndexProfileHash());
        snapshot.setUpdatedAt(Instant.now());
        knowledgeIndexSnapshotRepository.save(snapshot);
        return snapshot;
    }

    private static Map<String, Object> bootstrapIndexProfileJsonb() {
        return bootstrapIndexProfile().toSnapshotJsonb();
    }

    private static String bootstrapIndexProfileHash() {
        return bootstrapIndexProfile().profileHash();
    }

    private static ProjectIndexProfile bootstrapIndexProfile() {
        return new ProjectIndexProfile(
                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                MaterializationStrategy.CHUNK_LEVEL,
                false,
                null,
                "mxbai-embed-large",
                400,
                null,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.CHUNK_LEVEL,
                        false,
                        null,
                        "mxbai-embed-large",
                        400,
                        null),
                null,
                null);
    }

    private List<KnowledgeIndexSnapshotEntity> findActiveBootstrapSnapshots(UUID projectId) {
        String canonical = bootstrapSignatureForProject(projectId);
        List<KnowledgeIndexSnapshotEntity> rows = knowledgeIndexSnapshotRepository
                .findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(
                        projectId, canonical, IndexSnapshotStatus.ACTIVE);
        if (!rows.isEmpty()) {
            return rows;
        }
        String v0 = BOOTSTRAP_SIGNATURE_PREFIX_V0 + projectId;
        return knowledgeIndexSnapshotRepository.findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(
                projectId, v0, IndexSnapshotStatus.ACTIVE);
    }

    private KnowledgeIndexSnapshotEntity createActiveProjectSnapshot(ProjectEntity project, String signatureHash) {
        Instant now = Instant.now();
        KnowledgeIndexSnapshotEntity e = new KnowledgeIndexSnapshotEntity();
        e.setSignatureHash(signatureHash);
        e.setScopeType(KnowledgeSnapshotScopeType.PROJECT);
        e.setProject(project);
        e.setStatus(IndexSnapshotStatus.ACTIVE);
        e.setIndexProfileJsonb(bootstrapIndexProfileJsonb());
        e.setIndexProfileHash(bootstrapIndexProfileHash());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return knowledgeIndexSnapshotRepository.save(e);
    }
}
