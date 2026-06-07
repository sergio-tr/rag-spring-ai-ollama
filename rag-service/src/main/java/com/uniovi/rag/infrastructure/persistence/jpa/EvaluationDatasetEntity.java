package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssuePayload;
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

    @Column(name = "dataset_scope", nullable = false, length = 32)
    private String datasetScope;

    @Column(name = "storage_uri", columnDefinition = "text")
    private String storageUri;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "schema_version", length = 64)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benchmark_kinds_allowed", columnDefinition = "jsonb")
    private Map<String, Object> benchmarkKindsAllowed;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "experimental_kind", length = 64)
    private String experimentalKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_report_json", columnDefinition = "jsonb")
    private List<ValidationIssuePayload> validationIssues;

    @Column(name = "validation_status", length = 16)
    private String validationStatus;

    protected EvaluationDatasetEntity() {
    }

    /** Application-layer factory for Lab uploads (protected JPA constructor). */
    public static EvaluationDatasetEntity newLabUploadPlaceholder() {
        return new EvaluationDatasetEntity();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public EvaluationDatasetType getType() {
        return type;
    }

    public void setType(EvaluationDatasetType type) {
        this.type = type;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getDatasetScope() {
        return datasetScope;
    }

    public void setDatasetScope(String datasetScope) {
        this.datasetScope = datasetScope;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, Object> getBenchmarkKindsAllowed() {
        return benchmarkKindsAllowed;
    }

    public void setBenchmarkKindsAllowed(Map<String, Object> benchmarkKindsAllowed) {
        this.benchmarkKindsAllowed = benchmarkKindsAllowed;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExperimentalKind() {
        return experimentalKind;
    }

    public void setExperimentalKind(String experimentalKind) {
        this.experimentalKind = experimentalKind;
    }

    public List<ValidationIssuePayload> getValidationIssues() {
        return validationIssues;
    }

    public void setValidationIssues(List<ValidationIssuePayload> validationIssues) {
        this.validationIssues = validationIssues;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }
}
