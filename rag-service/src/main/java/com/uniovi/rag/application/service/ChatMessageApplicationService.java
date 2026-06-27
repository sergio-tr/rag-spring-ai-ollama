package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.MessageProcessingStatus;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.application.port.AfterCommitTaskScheduler;
import com.uniovi.rag.application.service.chat.ChatRuntimeCompatibilitySupport;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationDraftRepository;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationDraftEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.ConversationDraftDto;
import com.uniovi.rag.interfaces.rest.dto.PostMessageRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.application.service.async.AsyncLabTaskRunner;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.chat.async.ChatJobCancellationRegistry;
import com.uniovi.rag.application.service.chat.async.ChatJobPayloadKeys;
import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat message enqueue, draft persistence, edit/truncate, retry, and cooperative cancellation (one active chat job per conversation).
 */
@Service
public class ChatMessageApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageApplicationService.class);

    private final ProjectAccessService projectAccessService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationDraftRepository conversationDraftRepository;
    private final UserRepository userRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncLabTaskRunner asyncLabTaskRunner;
    private final AfterCommitTaskScheduler afterCommitTaskScheduler;
    private final ChatJobCancellationRegistry chatJobCancellationRegistry;
    private final AsyncTaskMutationService asyncTaskMutationService;
    private final ChatMessageWorkService chatMessageWorkService;
    private final RuntimeConfigValidationService runtimeConfigValidationService;
    private final ChatPresetDefaults chatPresetDefaults;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    public ChatMessageApplicationService(
            ProjectAccessService projectAccessService,
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationDraftRepository conversationDraftRepository,
            UserRepository userRepository,
            AsyncTaskRepository asyncTaskRepository,
            AsyncLabTaskRunner asyncLabTaskRunner,
            AfterCommitTaskScheduler afterCommitTaskScheduler,
            ChatJobCancellationRegistry chatJobCancellationRegistry,
            AsyncTaskMutationService asyncTaskMutationService,
            ChatMessageWorkService chatMessageWorkService,
            RuntimeConfigValidationService runtimeConfigValidationService,
            ChatPresetDefaults chatPresetDefaults,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.projectAccessService = projectAccessService;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationDraftRepository = conversationDraftRepository;
        this.userRepository = userRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncLabTaskRunner = asyncLabTaskRunner;
        this.afterCommitTaskScheduler = afterCommitTaskScheduler;
        this.chatJobCancellationRegistry = chatJobCancellationRegistry;
        this.asyncTaskMutationService = asyncTaskMutationService;
        this.chatMessageWorkService = chatMessageWorkService;
        this.runtimeConfigValidationService = runtimeConfigValidationService;
        this.chatPresetDefaults = chatPresetDefaults;
        this.runtimeObservability = runtimeObservability;
    }

    @Transactional
    public ChatMessageAcceptedDto enqueueMessage(UUID userId, UUID conversationId, PostMessageRequest body) {
        cancelPriorChatJobsForConversation(userId, conversationId);
        ConversationEntity conv = projectAccessService.requireConversationForUser(userId, conversationId);
        ProjectEntity project = conv.getProject();
        String content = body.content().trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        UUID presetId = conv.getPreset() != null ? conv.getPreset().getId() : null;
        UUID effectivePresetId = chatPresetDefaults.effectivePresetIdForApi(presetId);
        ChatRuntimeCompatibilitySupport.throwIfInvalid(
                runtimeConfigValidationService.validate(
                        userId,
                        new RuntimeConfigValidateRequest(
                                conversationId,
                                effectivePresetId != null ? effectivePresetId.toString() : null,
                                null,
                                Map.of())));
        UUID continueId = body.continueAfterUserMessageId();

        MessageEntity userMsg;
        MessageEntity asst;
        int max = messageRepository.findMaxSeqByConversationId(conversationId);

        if (continueId != null) {
            userMsg = messageRepository.findById(continueId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!userMsg.getConversation().getId().equals(conversationId) || userMsg.getRole() != MessageRole.USER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user message");
            }
            if (userMsg.getDeletedAt() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is deleted");
            }
            if (!userMsg.getContent().equals(content)) {
                userMsg.setContent(content);
                messageRepository.save(userMsg);
            }
            int aSeq = max + 1;
            asst = MessageEntity.assistantPlaceholder(conv, aSeq);
            messageRepository.save(asst);
        } else {
            int uSeq = max + 1;
            int aSeq = max + 2;
            userMsg = MessageEntity.userMessage(conv, content, uSeq);
            messageRepository.save(userMsg);
            asst = MessageEntity.assistantPlaceholder(conv, aSeq);
            messageRepository.save(asst);
        }

        conv.touchUpdated();
        conversationRepository.save(conv);
        String persistedLlm = conv.getLlmModel() != null && !conv.getLlmModel().isBlank() ? conv.getLlmModel().trim() : null;
        String effectiveLlm =
                body.llmModel() != null && !body.llmModel().isBlank() ? body.llmModel().trim() : persistedLlm;

        log.info(
                "chat_enqueue conversationId={} projectId={} userId={} presetId={} documentFilterCount={} llmModelOverrideSet={}",
                conversationId,
                project != null ? project.getId() : null,
                userId,
                conv.getPreset() != null ? conv.getPreset().getId() : null,
                conv.getDocumentFilter() != null ? conv.getDocumentFilter().size() : 0,
                effectiveLlm != null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChatJobPayloadKeys.CONVERSATION_ID, conversationId.toString());
        payload.put(ChatJobPayloadKeys.USER_MESSAGE_ID, userMsg.getId().toString());
        payload.put(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID, asst.getId().toString());
        payload.put(ChatJobPayloadKeys.LLM_MODEL, effectiveLlm);
        payload.put(ChatJobPayloadKeys.USER_TEXT, content);
        payload.put(
                ChatJobPayloadKeys.DOCUMENT_FILTER,
                conv.getDocumentFilter() != null ? conv.getDocumentFilter() : List.of());

        UserEntity user = userRepository.findById(userId).orElseThrow();
        AsyncTaskEntity task =
                AsyncTaskEntity.queued(user, project, AsyncTaskType.CHAT_MESSAGE, payload, Instant.now());
        asyncTaskRepository.save(task);
        UUID taskId = task.getId();
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.chatAccepted(
                    conversationId,
                    project != null ? project.getId() : null,
                    taskId,
                    userId);
        }
        afterCommitTaskScheduler.scheduleAfterCommit(() -> asyncLabTaskRunner.execute(taskId));
        return new ChatMessageAcceptedDto(taskId, userMsg.getId(), asst.getId());
    }

    @Transactional
    public ChatMessageAcceptedDto retryAssistantMessage(UUID userId, UUID conversationId, UUID assistantMessageId) {
        cancelPriorChatJobsForConversation(userId, conversationId);
        MessageEntity asst = messageRepository.findById(assistantMessageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!asst.getConversation().getId().equals(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        projectAccessService.requireConversationForUser(userId, conversationId);
        if (asst.getRole() != MessageRole.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not an assistant message");
        }
        if (asst.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is deleted");
        }
        if (asst.getStatus() != MessageProcessingStatus.ERROR
                && asst.getStatus() != MessageProcessingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant message is not in a retryable state");
        }
        MessageEntity userMsg =
                messageRepository
                        .findByConversation_IdAndSeqAndDeletedAtIsNull(conversationId, asst.getSeq() - 1)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user message"));
        if (userMsg.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid thread");
        }

        asst.setContent("");
        asst.setSources(null);
        asst.setQueryType(null);
        asst.setTraceId(null);
        asst.setPipelineSteps(null);
        asst.setExecutionMetadata(null);
        asst.setStatus(MessageProcessingStatus.PENDING);
        messageRepository.save(asst);

        ConversationEntity conv = conversationRepository.findById(conversationId).orElseThrow();
        String persistedLlm = conv.getLlmModel() != null && !conv.getLlmModel().isBlank() ? conv.getLlmModel().trim() : null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChatJobPayloadKeys.CONVERSATION_ID, conversationId.toString());
        payload.put(ChatJobPayloadKeys.USER_MESSAGE_ID, userMsg.getId().toString());
        payload.put(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID, asst.getId().toString());
        payload.put(ChatJobPayloadKeys.LLM_MODEL, persistedLlm);
        payload.put(ChatJobPayloadKeys.USER_TEXT, userMsg.getContent());
        payload.put(
                ChatJobPayloadKeys.DOCUMENT_FILTER,
                conv.getDocumentFilter() != null ? conv.getDocumentFilter() : List.of());

        UserEntity user = userRepository.findById(userId).orElseThrow();
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, conv.getProject().getId());
        AsyncTaskEntity task = AsyncTaskEntity.queued(user, project, AsyncTaskType.CHAT_MESSAGE, payload, Instant.now());
        asyncTaskRepository.save(task);
        UUID taskId = task.getId();
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.chatAccepted(
                    conversationId,
                    project != null ? project.getId() : null,
                    taskId,
                    userId);
        }
        afterCommitTaskScheduler.scheduleAfterCommit(() -> asyncLabTaskRunner.execute(taskId));
        return new ChatMessageAcceptedDto(taskId, userMsg.getId(), asst.getId());
    }

    @Transactional
    public void editUserMessage(UUID userId, UUID conversationId, UUID messageId, String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        cancelPriorChatJobsForConversation(userId, conversationId);
        MessageEntity m = messageRepository.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!m.getConversation().getId().equals(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        projectAccessService.requireConversationForUser(userId, conversationId);
        if (m.getRole() != MessageRole.USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only user messages can be edited");
        }
        if (m.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is deleted");
        }
        Instant now = Instant.now();
        List<MessageEntity> tail =
                messageRepository.findByConversation_IdAndSeqGreaterThanAndDeletedAtIsNullOrderBySeqAsc(
                        conversationId, m.getSeq());
        for (MessageEntity x : tail) {
            x.setDeletedAt(now);
            messageRepository.save(x);
        }
        m.setContent(newContent.trim());
        messageRepository.save(m);
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.touchUpdated();
            conversationRepository.save(c);
        });
    }

    @Transactional(readOnly = true)
    public ConversationDraftDto getDraft(UUID userId, UUID conversationId) {
        projectAccessService.requireConversationForUser(userId, conversationId);
        return conversationDraftRepository
                .findById(conversationId)
                .map(d -> new ConversationDraftDto(d.getContent(), d.getUpdatedAt()))
                .orElse(new ConversationDraftDto("", Instant.EPOCH));
    }

    @Transactional
    public ConversationDraftDto putDraft(UUID userId, UUID conversationId, String content) {
        ConversationEntity conv = projectAccessService.requireConversationForUser(userId, conversationId);
        Instant now = Instant.now();
        ConversationDraftEntity d =
                conversationDraftRepository
                        .findById(conversationId)
                        .orElseGet(() -> ConversationDraftEntity.create(conv, "", now));
        d.setContent(content != null ? content : "");
        d.setUpdatedAt(now);
        conversationDraftRepository.save(d);
        return new ConversationDraftDto(d.getContent(), d.getUpdatedAt());
    }

    @Transactional
    public void cancelChatTask(UUID userId, UUID taskId) {
        AsyncTaskEntity e =
                asyncTaskRepository
                        .findByIdAndUser_Id(taskId, userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (e.getTaskType() != AsyncTaskType.CHAT_MESSAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a chat task");
        }
        if (e.isTerminal()) {
            return;
        }
        chatJobCancellationRegistry.signalCancel(taskId);
        if (e.getStatus() == AsyncTaskStatus.QUEUED) {
            finalizeQueuedChatCancellation(e);
        }
    }

    private void cancelPriorChatJobsForConversation(UUID userId, UUID conversationId) {
        List<AsyncTaskEntity> tasks =
                asyncTaskRepository.findByUser_IdAndTaskTypeAndStatusIn(
                        userId,
                        AsyncTaskType.CHAT_MESSAGE,
                        List.of(AsyncTaskStatus.QUEUED, AsyncTaskStatus.RUNNING));
        for (AsyncTaskEntity t : tasks) {
            if (!payloadConversationIdMatches(t, conversationId)) {
                continue;
            }
            chatJobCancellationRegistry.signalCancel(t.getId());
            if (t.getStatus() == AsyncTaskStatus.QUEUED) {
                finalizeQueuedChatCancellation(t);
            }
        }
    }

    private static boolean payloadConversationIdMatches(AsyncTaskEntity task, UUID conversationId) {
        Map<String, Object> payload = task.getRequestPayload();
        if (payload == null) {
            return false;
        }
        Object raw = payload.get(ChatJobPayloadKeys.CONVERSATION_ID);
        return raw != null && conversationId.toString().equals(raw.toString());
    }

    private void finalizeQueuedChatCancellation(AsyncTaskEntity e) {
        Map<String, Object> p = e.getRequestPayload();
        if (p != null && p.get(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID) != null) {
            UUID assistantId = UUID.fromString(p.get(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID).toString());
            chatMessageWorkService.markAssistantCancelledForQueuedJob(assistantId);
        }
        asyncTaskMutationService.markCancelled(e.getId(), "Superseded or cancelled");
    }
}
