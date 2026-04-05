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

    @Column(name = "benchmark_kind", length = 64)
    private String benchmarkKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_payload", columnDefinition = "jsonb")
    private Map<String, Object> metricsPayload;

    public EvaluationResultEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EvaluationRunEntity getRun() {
        return run;
    }

    public void setRun(EvaluationRunEntity run) {
        this.run = run;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getActualAnswer() {
        return actualAnswer;
    }

    public void setActualAnswer(String actualAnswer) {
        this.actualAnswer = actualAnswer;
    }

    public Integer getCorrectness() {
        return correctness;
    }

    public void setCorrectness(Integer correctness) {
        this.correctness = correctness;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getBenchmarkKind() {
        return benchmarkKind;
    }

    public void setBenchmarkKind(String benchmarkKind) {
        this.benchmarkKind = benchmarkKind;
    }

    public Map<String, Object> getMetricsPayload() {
        return metricsPayload;
    }

    public void setMetricsPayload(Map<String, Object> metricsPayload) {
        this.metricsPayload = metricsPayload;
    }
}
