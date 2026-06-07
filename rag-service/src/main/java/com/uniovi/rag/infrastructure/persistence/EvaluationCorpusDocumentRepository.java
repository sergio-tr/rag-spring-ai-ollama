package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvaluationCorpusDocumentRepository
        extends JpaRepository<EvaluationCorpusDocumentEntity, EvaluationCorpusDocumentEntity.Key> {

    @Query(
            """
            SELECT d FROM KnowledgeDocumentEntity d
            JOIN EvaluationCorpusDocumentEntity link ON link.documentId = d.id
            WHERE link.corpusId = :corpusId
            ORDER BY link.addedAt ASC, d.id ASC
            """)
    List<KnowledgeDocumentEntity> findDocumentsByCorpusId(@Param("corpusId") UUID corpusId);

    long countByCorpusId(UUID corpusId);

    boolean existsByCorpusIdAndDocumentId(UUID corpusId, UUID documentId);
}
