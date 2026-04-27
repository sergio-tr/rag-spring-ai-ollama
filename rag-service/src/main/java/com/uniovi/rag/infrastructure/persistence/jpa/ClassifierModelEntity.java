package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ClassifierModelStatus;
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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "classifier_model")
public class ClassifierModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private EvaluationDatasetEntity dataset;

    @Column(name = "dataset_sha")
    private String datasetSha;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> hyperparams;

    private Double accuracy;

    @Column(name = "f1_macro")
    private Double f1Macro;

    @Column(name = "artifact_path")
    private String artifactPath;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "passes_gate", nullable = false)
    private boolean passesGate;

    @Column(name = "trained_at")
    private Instant trainedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClassifierModelStatus status;

    public ClassifierModelEntity() {
        // JPA requires a no-arg constructor for entity instantiation.
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

    public EvaluationDatasetEntity getDataset() {
        return dataset;
    }

    public void setDataset(EvaluationDatasetEntity dataset) {
        this.dataset = dataset;
    }

    public String getDatasetSha() {
        return datasetSha;
    }

    public void setDatasetSha(String datasetSha) {
        this.datasetSha = datasetSha;
    }

    public Map<String, Object> getHyperparams() {
        return hyperparams;
    }

    public void setHyperparams(Map<String, Object> hyperparams) {
        this.hyperparams = hyperparams;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Double getF1Macro() {
        return f1Macro;
    }

    public void setF1Macro(Double f1Macro) {
        this.f1Macro = f1Macro;
    }

    public String getArtifactPath() {
        return artifactPath;
    }

    public void setArtifactPath(String artifactPath) {
        this.artifactPath = artifactPath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isPassesGate() {
        return passesGate;
    }

    public void setPassesGate(boolean passesGate) {
        this.passesGate = passesGate;
    }

    public Instant getTrainedAt() {
        return trainedAt;
    }

    public void setTrainedAt(Instant trainedAt) {
        this.trainedAt = trainedAt;
    }

    public ClassifierModelStatus getStatus() {
        return status;
    }

    public void setStatus(ClassifierModelStatus status) {
        this.status = status;
    }
}
