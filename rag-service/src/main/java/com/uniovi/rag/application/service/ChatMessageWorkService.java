package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.MessageProcessingStatus;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import com.uniovi.rag.infrastructure.observability.TraceMdcBridge;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.application.service.runtime.ReasoningBlockSanitizer;
import com.uniovi.rag.interfaces.rest.support.UserFacingErrorSanitizer;
import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.application.service.runtime.ChatSourceMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence for chat message job outcomes (keeps async handler free of orchestration beyond RAG/LLM).
 */
@Service
public class ChatMessageWorkService {

    private static final int ASSISTANT_ERROR_MESSAGE_MAX_LEN = 600;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final Tracer tracer;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    public ChatMessageWorkService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            @Autowired(required = false) Tracer tracer,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.tracer = tracer;
        this.runtimeObservability = runtimeObservability;
    }

    @Transactional
    public void markAssistantProcessing(UUID assistantMessageId) {
        MessageEntity m = messageRepository.findById(assistantMessageId).orElseThrow();
        m.setStatus(MessageProcessingStatus.PROCESSING);
        messageRepository.save(m);
    }

    @Transactional
    public void applyAssistantSuccess(
            UUID assistantMessageId,
            UUID conversationId,
            String answer,
            List<ChatSource> sources,
            String queryType,
            String traceId,
            List<Map<String, Object>> pipelineSteps,
            String llmModel,
            Duration duration) {
        applyAssistantSuccess(
                assistantMessageId,
                conversationId,
                answer,
                sources,
                queryType,
                traceId,
                pipelineSteps,
                llmModel,
                duration,
                Map.of());
    }

    @Transactional
    public void applyAssistantSuccess(
            UUID assistantMessageId,
            UUID conversationId,
            String answer,
            List<ChatSource> sources,
            String queryType,
            String traceId,
            List<Map<String, Object>> pipelineSteps,
            String llmModel,
            Duration duration,
            Map<String, Object> chatTelemetry) {
        MessageEntity m = messageRepository.findById(assistantMessageId).orElseThrow();
        String visible = answer == null ? "" : ReasoningBlockSanitizer.sanitizeForUser(answer);
        m.setContent(visible);
        m.setSources(ChatSourceMapper.toPersistedMapsFromInternal(sources));
        m.setQueryType(queryType);
        m.setTraceId(traceId);
        m.setPipelineSteps(pipelineSteps);
        m.setStatus(MessageProcessingStatus.DONE);
        Map<String, Object> meta = new LinkedHashMap<>();
        if (llmModel != null && !llmModel.isBlank()) {
            meta.put("llmModel", llmModel);
        }
        meta.put("durationMs", duration != null ? duration.toMillis() : null);
        meta.put("documentCount", sources != null ? sources.size() : 0);
        if (chatTelemetry != null && !chatTelemetry.isEmpty()) {
            meta.putAll(chatTelemetry);
        }
        m.setExecutionMetadata(meta);
        messageRepository.save(m);
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.chatPersist(
                    assistantMessageId,
                    sources != null ? sources.size() : 0,
                    duration != null ? duration.toMillis() : 0L);
        }
        touchConversation(conversationId);
    }

    @Transactional
    public void applyAssistantCancelled(UUID assistantMessageId, UUID conversationId) {
        MessageEntity m = messageRepository.findById(assistantMessageId).orElseThrow();
        m.setStatus(MessageProcessingStatus.CANCELLED);
        messageRepository.save(m);
        touchConversation(conversationId);
    }

    @Transactional
    public void applyAssistantError(UUID assistantMessageId, UUID conversationId, String publicMessage) {
        MessageEntity m = messageRepository.findById(assistantMessageId).orElseThrow();
        String safe =
                UserFacingErrorSanitizer.sanitizeOrDefault(
                        publicMessage,
                        ASSISTANT_ERROR_MESSAGE_MAX_LEN,
                        "Sorry, we could not generate a reply. Please try again.");
        m.setContent(safe);
        m.setStatus(MessageProcessingStatus.ERROR);
        Map<String, Object> meta = m.getExecutionMetadata() != null
                ? new LinkedHashMap<>(m.getExecutionMetadata())
                : new LinkedHashMap<>();
        meta.put("error", safe);
        m.setExecutionMetadata(meta);
        messageRepository.save(m);
        touchConversation(conversationId);
    }

    @Transactional
    public void markAssistantCancelledForQueuedJob(UUID assistantMessageId) {
        messageRepository
                .findById(assistantMessageId)
                .ifPresent(m -> {
                    if (m.getRole() == MessageRole.ASSISTANT
                            && m.getStatus() != MessageProcessingStatus.DONE) {
                        m.setStatus(MessageProcessingStatus.CANCELLED);
                        messageRepository.save(m);
                    }
                });
    }

    private void touchConversation(UUID conversationId) {
        conversationRepository
                .findById(conversationId)
                .ifPresent(c -> {
                    c.touchUpdated();
                    conversationRepository.save(c);
                });
    }

    /** Best-effort trace id for message rows (Micrometer trace or MDC). */
    public String currentTraceId() {
        return TraceMdcBridge.currentCorrelationTraceId(tracer);
    }
}
