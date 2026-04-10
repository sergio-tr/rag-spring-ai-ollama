package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "runtime_execution_trace")
public class RuntimeExecutionTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "resolved_config_snapshot_id")
    private UUID resolvedConfigSnapshotId;

    @Column(name = "config_hash", columnDefinition = "text")
    private String configHash;

    @Column(name = "workflow_name", nullable = false, columnDefinition = "text")
    private String workflowName;

    @Column(name = "memory_attempted", nullable = false)
    private boolean memoryAttempted;

    @Column(name = "memory_outcome", nullable = false, columnDefinition = "text")
    private String memoryOutcome;

    @Column(name = "routing_attempted", nullable = false)
    private boolean routingAttempted;

    @Column(name = "routing_outcome", nullable = false, columnDefinition = "text")
    private String routingOutcome;

    @Column(name = "routing_route_kind", nullable = false, columnDefinition = "text")
    private String routingRouteKind;

    @Column(name = "routing_fallback_applied", nullable = false)
    private boolean routingFallbackApplied;

    @Column(name = "routing_workflow_selector_invoked", nullable = false)
    private boolean routingWorkflowSelectorInvoked;

    @Column(name = "deterministic_tool_outcome", nullable = false, columnDefinition = "text")
    private String deterministicToolOutcome;

    @Column(name = "function_calling_outcome", nullable = false, columnDefinition = "text")
    private String functionCallingOutcome;

    @Column(name = "advisor_outcome", nullable = false, columnDefinition = "text")
    private String advisorOutcome;

    @Column(name = "judge_attempted", nullable = false)
    private boolean judgeAttempted;

    @Column(name = "judge_candidate_source", nullable = false, columnDefinition = "text")
    private String judgeCandidateSource;

    @Column(name = "judge_final_outcome", nullable = false, columnDefinition = "text")
    private String judgeFinalOutcome;

    @Column(name = "judge_final_answer_from_retry", nullable = false)
    private boolean judgeFinalAnswerFromRetry;

    @Column(name = "clarification_outcome", nullable = false, columnDefinition = "text")
    private String clarificationOutcome;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_trace_jsonb", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> executionTraceJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stages_jsonb", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> stagesJsonb;

    public static RuntimeExecutionTraceEntity newForInsert() {
        RuntimeExecutionTraceEntity e = new RuntimeExecutionTraceEntity();
        e.createdAt = Instant.now();
        return e;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getResolvedConfigSnapshotId() {
        return resolvedConfigSnapshotId;
    }

    public void setResolvedConfigSnapshotId(UUID resolvedConfigSnapshotId) {
        this.resolvedConfigSnapshotId = resolvedConfigSnapshotId;
    }

    public String getConfigHash() {
        return configHash;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public boolean isMemoryAttempted() {
        return memoryAttempted;
    }

    public void setMemoryAttempted(boolean memoryAttempted) {
        this.memoryAttempted = memoryAttempted;
    }

    public String getMemoryOutcome() {
        return memoryOutcome;
    }

    public void setMemoryOutcome(String memoryOutcome) {
        this.memoryOutcome = memoryOutcome;
    }

    public boolean isRoutingAttempted() {
        return routingAttempted;
    }

    public void setRoutingAttempted(boolean routingAttempted) {
        this.routingAttempted = routingAttempted;
    }

    public String getRoutingOutcome() {
        return routingOutcome;
    }

    public void setRoutingOutcome(String routingOutcome) {
        this.routingOutcome = routingOutcome;
    }

    public String getRoutingRouteKind() {
        return routingRouteKind;
    }

    public void setRoutingRouteKind(String routingRouteKind) {
        this.routingRouteKind = routingRouteKind;
    }

    public boolean isRoutingFallbackApplied() {
        return routingFallbackApplied;
    }

    public void setRoutingFallbackApplied(boolean routingFallbackApplied) {
        this.routingFallbackApplied = routingFallbackApplied;
    }

    public boolean isRoutingWorkflowSelectorInvoked() {
        return routingWorkflowSelectorInvoked;
    }

    public void setRoutingWorkflowSelectorInvoked(boolean routingWorkflowSelectorInvoked) {
        this.routingWorkflowSelectorInvoked = routingWorkflowSelectorInvoked;
    }

    public String getDeterministicToolOutcome() {
        return deterministicToolOutcome;
    }

    public void setDeterministicToolOutcome(String deterministicToolOutcome) {
        this.deterministicToolOutcome = deterministicToolOutcome;
    }

    public String getFunctionCallingOutcome() {
        return functionCallingOutcome;
    }

    public void setFunctionCallingOutcome(String functionCallingOutcome) {
        this.functionCallingOutcome = functionCallingOutcome;
    }

    public String getAdvisorOutcome() {
        return advisorOutcome;
    }

    public void setAdvisorOutcome(String advisorOutcome) {
        this.advisorOutcome = advisorOutcome;
    }

    public boolean isJudgeAttempted() {
        return judgeAttempted;
    }

    public void setJudgeAttempted(boolean judgeAttempted) {
        this.judgeAttempted = judgeAttempted;
    }

    public String getJudgeCandidateSource() {
        return judgeCandidateSource;
    }

    public void setJudgeCandidateSource(String judgeCandidateSource) {
        this.judgeCandidateSource = judgeCandidateSource;
    }

    public String getJudgeFinalOutcome() {
        return judgeFinalOutcome;
    }

    public void setJudgeFinalOutcome(String judgeFinalOutcome) {
        this.judgeFinalOutcome = judgeFinalOutcome;
    }

    public boolean isJudgeFinalAnswerFromRetry() {
        return judgeFinalAnswerFromRetry;
    }

    public void setJudgeFinalAnswerFromRetry(boolean judgeFinalAnswerFromRetry) {
        this.judgeFinalAnswerFromRetry = judgeFinalAnswerFromRetry;
    }

    public String getClarificationOutcome() {
        return clarificationOutcome;
    }

    public void setClarificationOutcome(String clarificationOutcome) {
        this.clarificationOutcome = clarificationOutcome;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Map<String, Object> getExecutionTraceJsonb() {
        return executionTraceJsonb;
    }

    public void setExecutionTraceJsonb(Map<String, Object> executionTraceJsonb) {
        this.executionTraceJsonb = executionTraceJsonb;
    }

    public List<Map<String, Object>> getStagesJsonb() {
        return stagesJsonb;
    }

    public void setStagesJsonb(List<Map<String, Object>> stagesJsonb) {
        this.stagesJsonb = stagesJsonb;
    }
}

