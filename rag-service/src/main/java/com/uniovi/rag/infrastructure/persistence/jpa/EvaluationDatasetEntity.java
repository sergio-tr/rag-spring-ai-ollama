package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.EvaluationDatasetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_dataset")
public class EvaluationDatasetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "question_count")
    private Integer questionCount;

    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationDatasetType type;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected EvaluationDatasetEntity() {
    }

    public UUID getId() {
        return id;
    }
}
