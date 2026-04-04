package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "evaluation_result")
public class EvaluationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private EvaluationRunEntity run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private RagConfigurationEntity config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> configSnapshot;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "expected_answer", columnDefinition = "text")
    private String expectedAnswer;

    @Column(name = "actual_answer", columnDefinition = "text")
    private String actualAnswer;

    private Integer correctness;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> sources;

    @Column(name = "query_type")
    private String queryType;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected EvaluationResultEntity() {
    }

    public UUID getId() {
        return id;
    }
}
