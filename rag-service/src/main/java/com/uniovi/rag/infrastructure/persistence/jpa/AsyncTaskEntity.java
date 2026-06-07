package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
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
@Table(name = "async_task")
public class AsyncTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 64)
    private AsyncTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AsyncTaskStatus status;

    @Column(name = "progress_text", columnDefinition = "text")
    private String progressText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_log_json", columnDefinition = "jsonb")
    private Map<String, Object> eventLogJson;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AsyncTaskEntity() {
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

    public AsyncTaskType getTaskType() {
        return taskType;
    }

    public AsyncTaskStatus getStatus() {
        return status;
    }

    public void setStatus(AsyncTaskStatus status) {
        this.status = status;
    }

    public String getProgressText() {
        return progressText;
    }

    public void setProgressText(String progressText) {
        this.progressText = progressText;
    }

    public Map<String, Object> getRequestPayload() {
        return requestPayload;
    }

    public Map<String, Object> getResultJson() {
        return resultJson;
    }

    public void setResultJson(Map<String, Object> resultJson) {
        this.resultJson = resultJson;
    }

    public Map<String, Object> getEventLogJson() {
        return eventLogJson;
    }

    public void setEventLogJson(Map<String, Object> eventLogJson) {
        this.eventLogJson = eventLogJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isTerminal() {
        return status == AsyncTaskStatus.SUCCEEDED
                || status == AsyncTaskStatus.FAILED
                || status == AsyncTaskStatus.CANCELLED;
    }

    public static AsyncTaskEntity queued(UserEntity user, AsyncTaskType type, Map<String, Object> payload, Instant now) {
        return queued(user, null, type, payload, now);
    }

    public static AsyncTaskEntity queued(
            UserEntity user, ProjectEntity project, AsyncTaskType type, Map<String, Object> payload, Instant now) {
        AsyncTaskEntity e = new AsyncTaskEntity();
        e.user = user;
        e.project = project;
        e.taskType = type;
        e.status = AsyncTaskStatus.QUEUED;
        e.requestPayload = payload;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
