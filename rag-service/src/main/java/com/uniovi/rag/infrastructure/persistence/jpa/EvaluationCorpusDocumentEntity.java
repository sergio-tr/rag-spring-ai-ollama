package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "evaluation_corpus_document")
@IdClass(EvaluationCorpusDocumentEntity.Key.class)
public class EvaluationCorpusDocumentEntity {

    @Id
    @Column(name = "corpus_id", nullable = false)
    private UUID corpusId;

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corpus_id", insertable = false, updatable = false)
    private EvaluationCorpusEntity corpus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    private KnowledgeDocumentEntity document;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected EvaluationCorpusDocumentEntity() {}

    public static EvaluationCorpusDocumentEntity link(UUID corpusId, UUID documentId, Instant addedAt) {
        EvaluationCorpusDocumentEntity row = new EvaluationCorpusDocumentEntity();
        row.corpusId = corpusId;
        row.documentId = documentId;
        row.addedAt = addedAt != null ? addedAt : Instant.now();
        return row;
    }

    public UUID getCorpusId() {
        return corpusId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public static final class Key implements Serializable {
        private UUID corpusId;
        private UUID documentId;

        protected Key() {}

        public Key(UUID corpusId, UUID documentId) {
            this.corpusId = corpusId;
            this.documentId = documentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(corpusId, other.corpusId) && Objects.equals(documentId, other.documentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(corpusId, documentId);
        }
    }
}
