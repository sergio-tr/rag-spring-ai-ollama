package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent metadata backfill for legacy vector rows during snapshot transition.
 */
@Service
public class KnowledgeLegacyBackfillService {

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeIndexSnapshotService knowledgeIndexSnapshotService;
    private final ProjectRepository projectRepository;

    public KnowledgeLegacyBackfillService(
            JdbcTemplate jdbcTemplate,
            KnowledgeIndexSnapshotService knowledgeIndexSnapshotService,
            ProjectRepository projectRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeIndexSnapshotService = knowledgeIndexSnapshotService;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public int backfillProject(UUID projectId) {
        ProjectEntity project =
                projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("project not found"));
        var snap = knowledgeIndexSnapshotService.ensureLegacySnapshotForProject(project);
        String sid = snap.getId().toString();
        String merge =
                "{\"indexSnapshotId\": \"" + sid + "\", \"indexSignatureHash\": \"" + snap.getSignatureHash() + "\"}";
        return jdbcTemplate.update(
                """
                        UPDATE vector_store
                        SET metadata = metadata || ?::jsonb
                        WHERE (metadata->>'projectId') = ?
                          AND (metadata->>'indexSnapshotId') IS NULL
                        """,
                merge,
                projectId.toString());
    }
}
