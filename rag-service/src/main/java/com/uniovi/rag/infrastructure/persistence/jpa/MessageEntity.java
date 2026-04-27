package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.MessageProcessingStatus;
import com.uniovi.rag.domain.MessageRole;
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
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessageProcessingStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_metadata", columnDefinition = "jsonb")
    private Map<String, Object> executionMetadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> sources;

    @Column(name = "query_type")
    private String queryType;

    @Column(name = "trace_id")
    private String traceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pipeline_steps", columnDefinition = "jsonb")
    private List<Map<String, Object>> pipelineSteps;

    @Column(name = "grounding_score")
    private Double groundingScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MessageEntity() {
        // JPA requires a no-arg constructor for entity instantiation.
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public MessageProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(MessageProcessingStatus status) {
        this.status = status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Map<String, Object> getExecutionMetadata() {
        return executionMetadata;
    }

    public void setExecutionMetadata(Map<String, Object> executionMetadata) {
        this.executionMetadata = executionMetadata;
    }

    public List<Map<String, Object>> getSources() {
        return sources;
    }

    public void setSources(List<Map<String, Object>> sources) {
        this.sources = sources;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<Map<String, Object>> getPipelineSteps() {
        return pipelineSteps;
    }

    public void setPipelineSteps(List<Map<String, Object>> pipelineSteps) {
        this.pipelineSteps = pipelineSteps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static MessageEntity userMessage(ConversationEntity conversation, String content, int seq) {
        MessageEntity m = new MessageEntity();
        m.conversation = conversation;
        m.role = MessageRole.USER;
        m.content = content;
        m.seq = seq;
        m.status = MessageProcessingStatus.DONE;
        m.createdAt = Instant.now();
        return m;
    }

    public static MessageEntity assistantPlaceholder(ConversationEntity conversation, int seq) {
        MessageEntity m = new MessageEntity();
        m.conversation = conversation;
        m.role = MessageRole.ASSISTANT;
        m.content = "";
        m.seq = seq;
        m.status = MessageProcessingStatus.PENDING;
        m.createdAt = Instant.now();
        return m;
    }
}
