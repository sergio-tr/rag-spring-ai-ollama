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
import java.util.Map;
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

    @Column(name = "benchmark_kind", length = 64)
    private String benchmarkKind;

    @Column(name = "run_kind", length = 32)
    private String runKind;

    @Column(name = "workflow_schema_version", length = 32)
    private String workflowSchemaVersion;

    @Column(name = "dataset_sha256", length = 64)
    private String datasetSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_config_snapshot_id")
    private ResolvedConfigSnapshotEntity resolvedConfigSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "index_snapshot_id")
    private KnowledgeIndexSnapshotEntity indexSnapshot;

    @Column(name = "index_signature_hash", length = 128)
    private String indexSignatureHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preset_id")
    private RagPresetEntity preset;

    @Column(name = "llm_model_id", length = 256)
    private String llmModelId;

    @Column(name = "embedding_model_id", length = 256)
    private String embeddingModelId;

    @Column(name = "classifier_model_id", length = 256)
    private String classifierModelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "async_task_id")
    private AsyncTaskEntity asyncTask;

    @Column(name = "embedding_downstream_rag", nullable = false)
    private boolean embeddingDownstreamRag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_experimental_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> llmExperimentalSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding_experimental_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> embeddingExperimentalSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_profile_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> promptProfileSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aggregates_json", columnDefinition = "jsonb")
    private Map<String, Object> aggregatesJson;

    public EvaluationRunEntity() {
        // JPA requires a no-arg constructor for entity instantiation.
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public void setProject(ProjectEntity project) {
        this.project = project;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EvaluationDatasetEntity getDataset() {
        return dataset;
    }

    public void setDataset(EvaluationDatasetEntity dataset) {
        this.dataset = dataset;
    }

    public EvaluationRunType getType() {
        return type;
    }

    public void setType(EvaluationRunType type) {
        this.type = type;
    }

    public List<String> getConfigIds() {
        return configIds;
    }

    public void setConfigIds(List<String> configIds) {
        this.configIds = configIds;
    }

    public EvaluationRunStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationRunStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getBenchmarkKind() {
        return benchmarkKind;
    }

    public void setBenchmarkKind(String benchmarkKind) {
        this.benchmarkKind = benchmarkKind;
    }

    public String getRunKind() {
        return runKind;
    }

    public void setRunKind(String runKind) {
        this.runKind = runKind;
    }

    public String getWorkflowSchemaVersion() {
        return workflowSchemaVersion;
    }

    public void setWorkflowSchemaVersion(String workflowSchemaVersion) {
        this.workflowSchemaVersion = workflowSchemaVersion;
    }

    public String getDatasetSha256() {
        return datasetSha256;
    }

    public void setDatasetSha256(String datasetSha256) {
        this.datasetSha256 = datasetSha256;
    }

    public ResolvedConfigSnapshotEntity getResolvedConfigSnapshot() {
        return resolvedConfigSnapshot;
    }

    public void setResolvedConfigSnapshot(ResolvedConfigSnapshotEntity resolvedConfigSnapshot) {
        this.resolvedConfigSnapshot = resolvedConfigSnapshot;
    }

    public KnowledgeIndexSnapshotEntity getIndexSnapshot() {
        return indexSnapshot;
    }

    public void setIndexSnapshot(KnowledgeIndexSnapshotEntity indexSnapshot) {
        this.indexSnapshot = indexSnapshot;
    }

    public String getIndexSignatureHash() {
        return indexSignatureHash;
    }

    public void setIndexSignatureHash(String indexSignatureHash) {
        this.indexSignatureHash = indexSignatureHash;
    }

    public RagPresetEntity getPreset() {
        return preset;
    }

    public void setPreset(RagPresetEntity preset) {
        this.preset = preset;
    }

    public String getLlmModelId() {
        return llmModelId;
    }

    public void setLlmModelId(String llmModelId) {
        this.llmModelId = llmModelId;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public String getClassifierModelId() {
        return classifierModelId;
    }

    public void setClassifierModelId(String classifierModelId) {
        this.classifierModelId = classifierModelId;
    }

    public AsyncTaskEntity getAsyncTask() {
        return asyncTask;
    }

    public void setAsyncTask(AsyncTaskEntity asyncTask) {
        this.asyncTask = asyncTask;
    }

    public boolean isEmbeddingDownstreamRag() {
        return embeddingDownstreamRag;
    }

    public void setEmbeddingDownstreamRag(boolean embeddingDownstreamRag) {
        this.embeddingDownstreamRag = embeddingDownstreamRag;
    }

    public Map<String, Object> getLlmExperimentalSnapshot() {
        return llmExperimentalSnapshot;
    }

    public void setLlmExperimentalSnapshot(Map<String, Object> llmExperimentalSnapshot) {
        this.llmExperimentalSnapshot = llmExperimentalSnapshot;
    }

    public Map<String, Object> getEmbeddingExperimentalSnapshot() {
        return embeddingExperimentalSnapshot;
    }

    public void setEmbeddingExperimentalSnapshot(Map<String, Object> embeddingExperimentalSnapshot) {
        this.embeddingExperimentalSnapshot = embeddingExperimentalSnapshot;
    }

    public Map<String, Object> getPromptProfileSnapshot() {
        return promptProfileSnapshot;
    }

    public void setPromptProfileSnapshot(Map<String, Object> promptProfileSnapshot) {
        this.promptProfileSnapshot = promptProfileSnapshot;
    }

    public Map<String, Object> getAggregatesJson() {
        return aggregatesJson;
    }

    public void setAggregatesJson(Map<String, Object> aggregatesJson) {
        this.aggregatesJson = aggregatesJson;
    }
}
