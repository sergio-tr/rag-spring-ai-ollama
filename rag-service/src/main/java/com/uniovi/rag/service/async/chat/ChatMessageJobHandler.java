package com.uniovi.rag.service.async.chat;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.application.service.ChatMessageWorkService;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.async.lab.LabJobHandler;
import com.uniovi.rag.service.chat.ChatRetrievalSourceContributor;
import com.uniovi.rag.service.chat.ChatStreamChunks;
import com.uniovi.rag.service.query.ProcessQueryService;
import java.time.Duration;
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

    private final ProcessQueryService processQueryService;
    private final ChatRetrievalSourceContributor chatRetrievalSourceContributor;
    private final ChatJobCancellationRegistry cancellationRegistry;
    private final ChatMessageWorkService chatMessageWorkService;

    public ChatMessageJobHandler(
            ProcessQueryService processQueryService,
            ChatRetrievalSourceContributor chatRetrievalSourceContributor,
            ChatJobCancellationRegistry cancellationRegistry,
            ChatMessageWorkService chatMessageWorkService) {
        this.processQueryService = processQueryService;
        this.chatRetrievalSourceContributor = chatRetrievalSourceContributor;
        this.cancellationRegistry = cancellationRegistry;
        this.chatMessageWorkService = chatMessageWorkService;
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
            if (cancellationRegistry.isCancelled(taskId)) {
                finishCancelled(taskId, conversationId, assistantId, mutation);
                return;
            }
            QueryResponse qr =
                    processQueryService.generateResponseForChat(
                            userContent, llmModel, userId, projectId, conversationId, docFilter, userMessageId);
            String answer = qr.getAnswer() != null ? qr.getAnswer() : "";
            StringBuilder accumulated = new StringBuilder();
            for (String part : ChatStreamChunks.chunkForStream(answer)) {
                if (cancellationRegistry.isCancelled(taskId)) {
                    finishCancelled(taskId, conversationId, assistantId, mutation);
                    return;
                }
                accumulated.append(part);
                mutation.updateStreamingChatResult(taskId, accumulated.toString());
            }
            if (cancellationRegistry.isCancelled(taskId)) {
                finishCancelled(taskId, conversationId, assistantId, mutation);
                return;
            }
            List<Map<String, Object>> sources =
                    chatRetrievalSourceContributor.buildSources(
                            userId, projectId, conversationId, docFilter, userContent);
            List<Map<String, Object>> steps = buildPipelineSteps(qr);
            String qt = qr.getQueryType() != null ? qr.getQueryType().name() : null;
            String traceId = chatMessageWorkService.currentTraceId();
            Duration duration = Duration.between(start, Instant.now());
            chatMessageWorkService.applyAssistantSuccess(
                    assistantId,
                    conversationId,
                    answer,
                    sources,
                    qt,
                    traceId,
                    steps,
                    llmModel,
                    duration);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("answer", answer);
            result.put("queryType", qt);
            result.put("sources", sources);
            result.put("pipelineSteps", steps);
            result.put("phase", "done");
            mutation.markSucceeded(taskId, result);
        } catch (RagServiceException e) {
            log.warn("Chat job {} failed: {}", taskId, e.getMessage());
            chatMessageWorkService.applyAssistantError(assistantId, conversationId, e.getPublicMessage());
            mutation.markFailed(taskId, e.getPublicMessage());
        } catch (Exception e) {
            log.warn("Chat job {} failed", taskId, e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
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

    private static List<Map<String, Object>> buildPipelineSteps(QueryResponse qr) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of(
                "id", "classification",
                "name", "Classification",
                "detail", qr.getQueryType() != null ? qr.getQueryType().name() : ""));
        steps.add(Map.of(
                "id", "generation",
                "name", "Generation",
                "detail", qr.isUsedTool() && qr.getToolUsed() != null ? "tool:" + qr.getToolUsed() : "llm"));
        return steps;
    }
}
