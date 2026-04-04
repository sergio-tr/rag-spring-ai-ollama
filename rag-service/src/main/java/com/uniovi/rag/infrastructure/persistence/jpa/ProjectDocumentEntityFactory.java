package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ProjectDocumentStatus;

import java.time.Instant;

/**
 * Factory for {@link ProjectDocumentEntity} (minimal surface for v5 ingestion).
 */
public final class ProjectDocumentEntityFactory {

    private ProjectDocumentEntityFactory() {
    }

    public static ProjectDocumentEntity newIngesting(ProjectEntity project, String fileName) {
        ProjectDocumentEntity e = new ProjectDocumentEntity();
        e.setProject(project);
        e.setFileName(fileName);
        e.setStatus(ProjectDocumentStatus.INGESTING);
        e.setUploadedAt(Instant.now());
        return e;
    }
}
