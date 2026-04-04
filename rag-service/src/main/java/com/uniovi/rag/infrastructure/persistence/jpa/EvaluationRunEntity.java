package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.EvaluationRunType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evaluation_run")
public class EvaluationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private EvaluationDatasetEntity dataset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationRunType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> configIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationRunStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EvaluationRunEntity() {
    }

    public UUID getId() {
        return id;
    }

    public ProjectEntity getProject() {
        return project;
    }
}
