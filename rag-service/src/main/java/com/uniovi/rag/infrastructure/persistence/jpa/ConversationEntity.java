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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id")
    private RagConfigurationEntity config;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preset_id")
    private RagPresetEntity preset;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "classifier_model_id")
    private String classifierModelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_filter", nullable = false, columnDefinition = "jsonb")
    private List<String> documentFilter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_override_jsonb", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> runtimeOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_clarification_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> pendingClarification;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public void setProject(ProjectEntity project) {
        this.project = project;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getDocumentFilter() {
        return documentFilter;
    }

    /** Replaces the persisted per-chat document subset (empty = no restriction). */
    public void setDocumentFilter(List<String> documentFilter) {
        this.documentFilter = documentFilter != null ? documentFilter : List.of();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public RagPresetEntity getPreset() {
        return preset;
    }

    public void setPreset(RagPresetEntity preset) {
        this.preset = preset;
    }

    public RagConfigurationEntity getConfig() {
        return config;
    }

    public void setConfig(RagConfigurationEntity config) {
        this.config = config;
    }

    public Map<String, Object> getRuntimeOverride() {
        return runtimeOverride != null ? runtimeOverride : Map.of();
    }

    public void setRuntimeOverride(Map<String, Object> runtimeOverride) {
        this.runtimeOverride = runtimeOverride != null ? new LinkedHashMap<>(runtimeOverride) : new LinkedHashMap<>();
    }

    /** Per-conversation LLM id (canonical); merged after {@code runtime_override_jsonb} into RAG config for this chat. */
    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    /** Classifier-service inference tag for this chat (canonical); merged after runtime JSON. */
    public String getClassifierModelId() {
        return classifierModelId;
    }

    public void setClassifierModelId(String classifierModelId) {
        this.classifierModelId = classifierModelId;
    }

    public Map<String, Object> getPendingClarification() {
        return pendingClarification;
    }

    public void setPendingClarification(Map<String, Object> pendingClarification) {
        this.pendingClarification = pendingClarification;
    }

    public void touchUpdated() {
        this.updatedAt = Instant.now();
    }

    public static ConversationEntity create(UserEntity user, ProjectEntity project, String title,
                                            List<String> documentFilter) {
        ConversationEntity c = new ConversationEntity();
        c.user = user;
        c.project = project;
        c.title = title != null ? title : "Chat";
        c.documentFilter = documentFilter != null ? documentFilter : List.of();
        c.runtimeOverride = new LinkedHashMap<>();
        Instant now = Instant.now();
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }
}
