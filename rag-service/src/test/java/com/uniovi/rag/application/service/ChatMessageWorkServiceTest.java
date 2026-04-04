package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.MessageProcessingStatus;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageWorkServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ChatMessageWorkService chatMessageWorkService;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void markAssistantProcessing_setsStatus() {
        UUID id = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        when(messageRepository.findById(id)).thenReturn(Optional.of(m));

        chatMessageWorkService.markAssistantProcessing(id);

        assertThat(m.getStatus()).isEqualTo(MessageProcessingStatus.PROCESSING);
        verify(messageRepository).save(m);
    }

    @Test
    void applyAssistantSuccess_setsMetadataAndTouchesConversation() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        ConversationEntity conv = mock(ConversationEntity.class);
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(m));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        List<Map<String, Object>> sources = List.of(Map.of("id", "d1"));
        chatMessageWorkService.applyAssistantSuccess(
                assistantId,
                convId,
                "answer",
                sources,
                "COUNT",
                "trace-1",
                List.of(),
                "llama",
                Duration.ofMillis(1500));

        assertThat(m.getContent()).isEqualTo("answer");
        assertThat(m.getSources()).isEqualTo(sources);
        assertThat(m.getQueryType()).isEqualTo("COUNT");
        assertThat(m.getTraceId()).isEqualTo("trace-1");
        assertThat(m.getStatus()).isEqualTo(MessageProcessingStatus.DONE);
        Map<String, Object> meta = m.getExecutionMetadata();
        assertThat(meta).containsEntry("llmModel", "llama");
        assertThat(meta).containsEntry("durationMs", 1500L);
        assertThat(meta).containsEntry("documentCount", 1);
        verify(conversationRepository).save(conv);
    }

    @Test
    void applyAssistantSuccess_nullAnswerUsesEmptyStringAndSkipsBlankLlmModel() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(m));
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        chatMessageWorkService.applyAssistantSuccess(
                assistantId, convId, null, null, null, null, null, "  ", null);

        assertThat(m.getContent()).isEmpty();
        assertThat(m.getExecutionMetadata()).doesNotContainKey("llmModel");
        assertThat(m.getExecutionMetadata()).containsEntry("documentCount", 0);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void applyAssistantCancelled_setsCancelled() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        ConversationEntity conv = mock(ConversationEntity.class);
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(m));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        chatMessageWorkService.applyAssistantCancelled(assistantId, convId);

        assertThat(m.getStatus()).isEqualTo(MessageProcessingStatus.CANCELLED);
        verify(conversationRepository).save(conv);
    }

    @Test
    void applyAssistantError_mergesIntoExistingMetadata() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        m.setExecutionMetadata(new LinkedHashMap<>(Map.of("prev", 1)));
        ConversationEntity conv = mock(ConversationEntity.class);
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(m));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));

        chatMessageWorkService.applyAssistantError(assistantId, convId, "oops");

        assertThat(m.getStatus()).isEqualTo(MessageProcessingStatus.ERROR);
        assertThat(m.getExecutionMetadata()).containsEntry("error", "oops");
        assertThat(m.getExecutionMetadata()).containsEntry("prev", 1);
    }

    @Test
    void markAssistantCancelledForQueuedJob_skipsWhenDoneOrNotAssistant() {
        UUID id = UUID.randomUUID();
        MessageEntity done = new MessageEntity();
        done.setRole(MessageRole.ASSISTANT);
        done.setStatus(MessageProcessingStatus.DONE);
        MessageEntity user = new MessageEntity();
        user.setRole(MessageRole.USER);
        user.setStatus(MessageProcessingStatus.PENDING);

        when(messageRepository.findById(id)).thenReturn(Optional.of(done));
        chatMessageWorkService.markAssistantCancelledForQueuedJob(id);
        verify(messageRepository, never()).save(any());

        when(messageRepository.findById(id)).thenReturn(Optional.of(user));
        chatMessageWorkService.markAssistantCancelledForQueuedJob(id);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void markAssistantCancelledForQueuedJob_cancelsPendingAssistant() {
        UUID id = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        m.setRole(MessageRole.ASSISTANT);
        m.setStatus(MessageProcessingStatus.PENDING);
        when(messageRepository.findById(id)).thenReturn(Optional.of(m));

        chatMessageWorkService.markAssistantCancelledForQueuedJob(id);

        assertThat(m.getStatus()).isEqualTo(MessageProcessingStatus.CANCELLED);
        verify(messageRepository).save(m);
    }

    @Test
    void currentTraceId_readsMdc() {
        MDC.put("traceId", "abc");
        assertThat(ChatMessageWorkService.currentTraceId()).isEqualTo("abc");
    }

    @Test
    void applyAssistantError_createsMetadataWhenAbsent() {
        UUID assistantId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        MessageEntity m = new MessageEntity();
        m.setExecutionMetadata(null);
        when(messageRepository.findById(assistantId)).thenReturn(Optional.of(m));
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        chatMessageWorkService.applyAssistantError(assistantId, convId, "e1");

        verify(messageRepository).save(m);
        assertThat(m.getExecutionMetadata()).containsEntry("error", "e1");
    }
}
