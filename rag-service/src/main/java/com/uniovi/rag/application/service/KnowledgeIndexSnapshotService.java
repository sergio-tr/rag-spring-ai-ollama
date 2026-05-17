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
 * Logical index snapshots, including synthetic LEGACY rows per project during transition.
 */
@Service
public class KnowledgeIndexSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexSnapshotService.class);

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
        List<KnowledgeIndexSnapshotEntity> rows = knowledgeIndexSnapshotRepository
                .findByProject_IdAndSignatureHashAndStatusOrderByUpdatedAtDesc(pid, sig, IndexSnapshotStatus.ACTIVE);
        if (rows.size() > 1) {
            log.warn(
                    "Multiple ACTIVE LEGACY snapshots found for project {} and signature {} (count={}); using most recent",
                    pid,
                    sig,
                    rows.size());
        }
        return rows.stream()
                .findFirst()
                .map(this::ensureLegacySnapshotHasCapabilities)
                .orElseGet(() -> createActiveProjectSnapshot(project, sig));
    }

    private KnowledgeIndexSnapshotEntity ensureLegacySnapshotHasCapabilities(KnowledgeIndexSnapshotEntity snapshot) {
        Map<String, Object> profile = snapshot.getIndexProfileJsonb();
        String hash = snapshot.getIndexProfileHash();
        boolean missingProfile = profile == null || profile.isEmpty();
        boolean missingHash = hash == null || hash.isBlank();
        if (!missingProfile && !missingHash) {
            return snapshot;
        }
        snapshot.setIndexProfileJsonb(legacyIndexProfileJsonb());
        snapshot.setIndexProfileHash(legacyIndexProfileHash());
        snapshot.setUpdatedAt(Instant.now());
        knowledgeIndexSnapshotRepository.save(snapshot);
        return snapshot;
    }

    private static Map<String, Object> legacyIndexProfileJsonb() {
        return legacyIndexProfile().toSnapshotJsonb();
    }

    private static String legacyIndexProfileHash() {
        return legacyIndexProfile().profileHash();
    }

    private static ProjectIndexProfile legacyIndexProfile() {
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

    private KnowledgeIndexSnapshotEntity createActiveProjectSnapshot(ProjectEntity project, String signatureHash) {
        Instant now = Instant.now();
        KnowledgeIndexSnapshotEntity e = new KnowledgeIndexSnapshotEntity();
        e.setSignatureHash(signatureHash);
        e.setScopeType(KnowledgeSnapshotScopeType.PROJECT);
        e.setProject(project);
        e.setStatus(IndexSnapshotStatus.ACTIVE);
        e.setIndexProfileJsonb(legacyIndexProfileJsonb());
        e.setIndexProfileHash(legacyIndexProfileHash());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return knowledgeIndexSnapshotRepository.save(e);
    }
}
