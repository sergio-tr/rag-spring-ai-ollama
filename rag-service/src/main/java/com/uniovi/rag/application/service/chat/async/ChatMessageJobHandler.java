package com.uniovi.rag.application.service.chat.async;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.ChatSourceMapper;
import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.ChatMessageWorkService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.interfaces.rest.support.UserFacingErrorSanitizer;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.async.LabJobHandler;
import com.uniovi.rag.application.service.chat.ChatStreamChunks;
import com.uniovi.rag.application.service.runtime.execution.RuntimeQueryExecutionService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE}: RAG + LLM, cooperative cancel, streamed progress in task result.
 */
@Component
public class ChatMessageJobHandler implements LabJobHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageJobHandler.class);

    private static final int GENERIC_FAILURE_MESSAGE_MAX_LEN = 600;

    private final RuntimeQueryExecutionService runtimeQueryExecutionService;
    private final ChatJobCancellationRegistry cancellationRegistry;
    private final ChatMessageWorkService chatMessageWorkService;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    public ChatMessageJobHandler(
            RuntimeQueryExecutionService runtimeQueryExecutionService,
            ChatJobCancellationRegistry cancellationRegistry,
            ChatMessageWorkService chatMessageWorkService,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.runtimeQueryExecutionService = runtimeQueryExecutionService;
        this.cancellationRegistry = cancellationRegistry;
        this.chatMessageWorkService = chatMessageWorkService;
        this.runtimeObservability = runtimeObservability;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.CHAT_MESSAGE;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        Map<String, Object> p = task.getRequestPayload();
        if (p == null) {
            mutation.markFailed(taskId, "Missing request payload");
            return;
        }
        UUID conversationId = UUID.fromString(p.get(ChatJobPayloadKeys.CONVERSATION_ID).toString());
        UUID assistantId = UUID.fromString(p.get(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID).toString());
        UUID userMessageId = UUID.fromString(p.get(ChatJobPayloadKeys.USER_MESSAGE_ID).toString());
        UUID userId = task.getUser().getId();
        UUID projectId = task.getProject() != null ? task.getProject().getId() : null;
        if (projectId == null) {
            mutation.markFailed(taskId, "Missing project scope on chat task");
            return;
        }
        String llmModel = p.get(ChatJobPayloadKeys.LLM_MODEL) != null ? p.get(ChatJobPayloadKeys.LLM_MODEL).toString() : null;
        Object ut = p.get(ChatJobPayloadKeys.USER_TEXT);
        if (ut == null || ut.toString().isBlank()) {
            mutation.markFailed(taskId, "Missing userText in payload");
            return;
        }
        String userContent = ut.toString();
        @SuppressWarnings("unchecked")
        List<String> docFilter =
                p.get(ChatJobPayloadKeys.DOCUMENT_FILTER) instanceof List<?> l
                        ? l.stream().map(Object::toString).toList()
                        : List.of();

        chatMessageWorkService.markAssistantProcessing(assistantId);

        Instant start = Instant.now();
        try {
            log.info(
                    "chat_runtime_start taskId={} conversationId={} projectId={} userId={} documentFilterCount={}",
                    taskId,
                    conversationId,
                    projectId,
                    userId,
                    docFilter.size());
            if (cancellationRegistry.isCancelled(taskId)) {
                finishCancelled(taskId, conversationId, assistantId, mutation);
                return;
            }
            QueryResponse qr =
                    runtimeQueryExecutionService.generateResponseForChat(
                            userContent, llmModel, userId, projectId, conversationId, docFilter, userMessageId);
            String answer = qr.getAnswer() != null ? qr.getAnswer() : "";
            StringBuilder accumulated = new StringBuilder();
            for (String part : ChatStreamChunks.chunkForStream(answer)) {
                if (cancellationRegistry.isCancelled(taskId)) {
                    if (persistSuccessDespiteCancellationIfSafe(
                            taskId, conversationId, assistantId, mutation, qr, answer, llmModel, start)) {
                        return;
                    }
                    finishCancelled(taskId, conversationId, assistantId, mutation);
                    return;
                }
                accumulated.append(part);
                mutation.updateStreamingChatResult(taskId, accumulated.toString());
            }
            if (cancellationRegistry.isCancelled(taskId)) {
                if (persistSuccessDespiteCancellationIfSafe(
                        taskId, conversationId, assistantId, mutation, qr, answer, llmModel, start)) {
                    return;
                }
                finishCancelled(taskId, conversationId, assistantId, mutation);
                return;
            }
            log.info(
                    "chat_runtime_result taskId={} conversationId={} projectId={} sourceCount={}",
                    taskId,
                    conversationId,
                    projectId,
                    qr.getSources() != null ? qr.getSources().size() : 0);
            persistAssistantSuccess(taskId, conversationId, assistantId, mutation, qr, answer, llmModel, start);
        } catch (RagServiceException e) {
            log.warn("Chat job {} failed: {}", taskId, e.getMessage());
            recordChatFailure(e.getErrorCode() != null ? e.getErrorCode().name() : "unknown");
            chatMessageWorkService.applyAssistantError(assistantId, conversationId, e.getPublicMessage());
            mutation.markFailed(taskId, e.getPublicMessage(), e.getErrorCode().name());
        } catch (Exception e) {
            log.warn("Chat job {} failed", taskId, e);
            recordChatFailure("unknown");
            String msg =
                    UserFacingErrorSanitizer.sanitizeOrDefault(
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                            GENERIC_FAILURE_MESSAGE_MAX_LEN,
                            "Something went wrong while generating a reply.");
            chatMessageWorkService.applyAssistantError(assistantId, conversationId, msg);
            mutation.markFailed(taskId, msg);
        } finally {
            cancellationRegistry.clear(taskId);
        }
    }

    private void finishCancelled(
            UUID taskId, UUID conversationId, UUID assistantId, AsyncTaskMutationService mutation) {
        chatMessageWorkService.applyAssistantCancelled(assistantId, conversationId);
        mutation.markCancelled(taskId, "Stopped");
    }

    /**
     * Late supersede/cancel signals after RAG should not discard a non-empty deterministic answer.
     *
     * @return true when success was persisted and the job should exit without marking cancelled
     */
    private boolean persistSuccessDespiteCancellationIfSafe(
            UUID taskId,
            UUID conversationId,
            UUID assistantId,
            AsyncTaskMutationService mutation,
            QueryResponse qr,
            String answer,
            String llmModel,
            Instant start) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        log.info(
                "chat_runtime_late_cancel_persist taskId={} conversationId={} answerLength={}",
                taskId,
                conversationId,
                answer.length());
        persistAssistantSuccess(taskId, conversationId, assistantId, mutation, qr, answer, llmModel, start);
        return true;
    }

    private void persistAssistantSuccess(
            UUID taskId,
            UUID conversationId,
            UUID assistantId,
            AsyncTaskMutationService mutation,
            QueryResponse qr,
            String answer,
            String llmModel,
            Instant start) {
        List<Map<String, Object>> steps = buildPipelineSteps(qr);
        String qt = qr.getQueryType() != null ? qr.getQueryType().name() : null;
        String traceId = chatMessageWorkService.currentTraceId();
        Duration duration = Duration.between(start, Instant.now());
        Map<String, Object> telemetry = qr.getChatTelemetry() != null ? qr.getChatTelemetry() : Map.of();
        List<ChatSource> sources = qr.getSources() != null ? qr.getSources() : List.of();
        chatMessageWorkService.applyAssistantSuccess(
                assistantId,
                conversationId,
                answer,
                sources,
                qt,
                traceId,
                steps,
                llmModel,
                duration,
                telemetry);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("queryType", qt);
        result.put("sources", ChatSourceMapper.toPersistedMapsFromInternal(sources));
        result.put("pipelineSteps", steps);
        result.put("phase", "done");
        mutation.markSucceeded(taskId, result);
    }

    private void recordChatFailure(String errorCode) {
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.chatFailed(errorCode);
        }
    }

    private static List<Map<String, Object>> buildPipelineSteps(QueryResponse qr) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of(
                "id", "classification",
                "name", "Classification",
                "detail", qr.getQueryType() != null ? qr.getQueryType().name() : ""));
        Map<String, Object> tel = qr.getChatTelemetry() != null ? qr.getChatTelemetry() : Map.of();
        if (Boolean.TRUE.equals(tel.get("clarificationRequired"))) {
            steps.add(Map.of(
                    "id",
                    "clarification",
                    "name",
                    "Clarification",
                    "detail",
                    tel.get("clarificationOutcome") != null
                            ? String.valueOf(tel.get("clarificationOutcome"))
                            : "pending"));
        }
        if (Boolean.TRUE.equals(tel.get("routingAttempted"))) {
            String route = tel.get("routingRouteKind") != null ? String.valueOf(tel.get("routingRouteKind")) : "";
            String fallback =
                    Boolean.TRUE.equals(tel.get("routingFallbackApplied"))
                            ? "fallback="
                                    + (tel.get("routingFallbackRouteKind") != null
                                            ? String.valueOf(tel.get("routingFallbackRouteKind"))
                                            : "?")
                            : "fallback=no";
            steps.add(Map.of(
                    "id",
                    "routing",
                    "name",
                    "Adaptive routing",
                    "detail",
                    (tel.get("routingOutcome") != null ? String.valueOf(tel.get("routingOutcome")) + " " : "")
                            + route
                            + " "
                            + fallback));
        }
        if (Boolean.TRUE.equals(tel.get("memoryAttempted"))) {
            steps.add(Map.of(
                    "id",
                    "memory",
                    "name",
                    "Memory",
                    "detail",
                    tel.get("memoryOutcome") != null ? String.valueOf(tel.get("memoryOutcome")) : "attempted"));
        }
        if (Boolean.TRUE.equals(tel.get("reasoningAttempted"))) {
            steps.add(Map.of(
                    "id",
                    "reasoning",
                    "name",
                    "Reasoning",
                    "detail",
                    "strategy="
                            + (tel.get("reasoningStrategy") != null ? tel.get("reasoningStrategy") : "")
                            + " summary="
                            + (tel.get("reasoningPlanSummaryTruncated") != null
                                    ? tel.get("reasoningPlanSummaryTruncated")
                                    : "")));
        }
        if (tel.containsKey("retrievalRerankApplied")) {
            steps.add(Map.of(
                    "id",
                    "retrieval_advanced",
                    "name",
                    "Retrieval (rank / post)",
                    "detail",
                    "rerank="
                            + tel.get("retrievalRerankApplied")
                            + " counts fusion="
                            + tel.getOrDefault("retrievalAfterFusionCount", "?")
                            + " rerank="
                            + tel.getOrDefault("retrievalAfterRerankCount", "?")
                            + " filter="
                            + tel.getOrDefault("retrievalAfterFilterCount", "?")
                            + " compress="
                            + tel.getOrDefault("retrievalAfterCompressionCount", "?")
                            + " protected="
                            + tel.getOrDefault("retrievalProtectedCandidateCount", "?")
                            + " dropped="
                            + tel.getOrDefault("retrievalDroppedCandidateCount", "?")));
        }
        if (Boolean.TRUE.equals(tel.get("judgeAttempted"))) {
            steps.add(Map.of(
                    "id",
                    "judge",
                    "name",
                    "Judge",
                    "detail",
                    (tel.get("judgeFinalOutcome") != null ? String.valueOf(tel.get("judgeFinalOutcome")) : "")
                            + (Boolean.TRUE.equals(tel.get("judgeFinalAnswerFromRetry")) ? " (retry answer)" : "")));
        }
        steps.add(Map.of(
                "id", "generation",
                "name", "Generation",
                "detail", qr.isUsedTool() && qr.getToolUsed() != null ? "tool:" + qr.getToolUsed() : "llm"));
        return steps;
    }
}
