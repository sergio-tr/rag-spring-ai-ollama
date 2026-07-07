package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.infrastructure.persistence.jpa.DocumentArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifactEntity, UUID> {

    List<DocumentArtifactEntity> findByDocument_IdOrderByCreatedAtAsc(UUID documentId);

    /**
     * Removes prior rows of the same (document, artifactType) before a re-materialization writes a fresh one.
     * Without this, every re-index / re-materialization event accumulates an unbounded number of duplicate
     * artifact rows (observed up to 35x for a single document), which inflates any downstream query that
     * loads artifacts by document id — notably {@code MetadataAppendixLoader}, where duplicated METADATA
     * payloads bloated the LLM prompt to 250k+ chars and caused truncated/garbage final answers.
     */
    long deleteByDocument_IdAndArtifactType(UUID documentId, DocumentArtifactType artifactType);
}
