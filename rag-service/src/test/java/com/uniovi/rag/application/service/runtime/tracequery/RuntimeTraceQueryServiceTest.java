package com.uniovi.rag.application.service.runtime.tracequery;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceRepository;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeTraceQueryServiceTest {

    @Test
    void getTraceDetailById_toleratesNullValuesInJsonb_withoutThrowing() {
        RuntimeExecutionTraceRepository repo = mock(RuntimeExecutionTraceRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        MessageRepository messageRepo = mock(MessageRepository.class);
        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repo, access, messageRepo);

        UUID userId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        RuntimeExecutionTraceEntity e = mock(RuntimeExecutionTraceEntity.class);
        when(e.getId()).thenReturn(traceId);
        when(e.getCreatedAt()).thenReturn(Instant.now());
        when(e.getUserId()).thenReturn(userId);
        when(e.getProjectId()).thenReturn(UUID.randomUUID());
        when(e.getCorrelationId()).thenReturn("cid");
        when(e.getWorkflowName()).thenReturn("wf");
        when(e.getSchemaVersion()).thenReturn(2);

        Map<String, Object> jsonWithNull = new HashMap<>();
        jsonWithNull.put("retrievalDiagnostics", null);
        jsonWithNull.put("note", "ok");
        when(e.getExecutionTraceJsonb()).thenReturn(jsonWithNull);
        when(e.getStagesJsonb()).thenReturn(null);

        when(repo.findById(traceId)).thenReturn(Optional.of(e));

        var dto = svc.getTraceDetailById(userId, traceId);
        assertThat(dto.id()).isEqualTo(traceId);
        assertThat(dto.executionTraceJson()).containsKey("retrievalDiagnostics");
        assertThat(dto.executionTraceJson().get("retrievalDiagnostics")).isNull();
    }

    @Test
    void getMostRecentTraceDetailByMessageId_resolvesAssistantMessageId_toPreviousUserMessageId_whenPossible() {
        RuntimeExecutionTraceRepository repo = mock(RuntimeExecutionTraceRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        MessageRepository messageRepo = mock(MessageRepository.class);
        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repo, access, messageRepo);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();

        when(repo.findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(userId, assistantMessageId))
                .thenReturn(Optional.empty());

        ConversationEntity conv = mock(ConversationEntity.class);
        when(conv.getId()).thenReturn(conversationId);
        MessageEntity assistant = mock(MessageEntity.class);
        when(assistant.getConversation()).thenReturn(conv);
        when(assistant.getRole()).thenReturn(MessageRole.ASSISTANT);
        when(assistant.getSeq()).thenReturn(2);
        when(messageRepo.findById(assistantMessageId)).thenReturn(Optional.of(assistant));

        MessageEntity prevUser = mock(MessageEntity.class);
        when(prevUser.getRole()).thenReturn(MessageRole.USER);
        when(prevUser.getId()).thenReturn(userMessageId);
        when(messageRepo.findByConversation_IdAndSeqAndDeletedAtIsNull(conversationId, 1))
                .thenReturn(Optional.of(prevUser));

        RuntimeExecutionTraceEntity trace = mock(RuntimeExecutionTraceEntity.class);
        UUID traceId = UUID.randomUUID();
        when(trace.getId()).thenReturn(traceId);
        when(trace.getUserId()).thenReturn(userId);
        when(trace.getCreatedAt()).thenReturn(Instant.now());
        when(trace.getConversationId()).thenReturn(conversationId);
        when(trace.getMessageId()).thenReturn(userMessageId);
        when(trace.getCorrelationId()).thenReturn("cid");
        when(trace.getWorkflowName()).thenReturn("wf");
        when(trace.getSchemaVersion()).thenReturn(2);
        when(trace.getExecutionTraceJsonb()).thenReturn(Map.of());
        when(trace.getStagesJsonb()).thenReturn(List.of());

        when(repo.findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(userId, userMessageId))
                .thenReturn(Optional.of(trace));

        var dto = svc.getMostRecentTraceDetailByMessageId(userId, conversationId, assistantMessageId);
        assertThat(dto.id()).isEqualTo(traceId);
        assertThat(dto.messageId()).isEqualTo(userMessageId);
    }

    @Test
    void getMostRecentTraceDetailByMessageId_returnsNotFound_whenAssistantCannotBeResolved() {
        RuntimeExecutionTraceRepository repo = mock(RuntimeExecutionTraceRepository.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        MessageRepository messageRepo = mock(MessageRepository.class);
        RuntimeTraceQueryService svc = new RuntimeTraceQueryService(repo, access, messageRepo);

        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();

        when(repo.findFirstByUserIdAndMessageIdOrderByCreatedAtDesc(userId, assistantMessageId))
                .thenReturn(Optional.empty());
        when(messageRepo.findById(assistantMessageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.getMostRecentTraceDetailByMessageId(userId, conversationId, assistantMessageId))
                .isInstanceOf(NotFoundException.class);
    }
}

