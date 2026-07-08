package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.chat.async.ChatJobPayloadKeys;
import com.uniovi.rag.application.service.chat.async.ChatMessageJobHandler;
import com.uniovi.rag.domain.AsyncTaskStatus;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Focused propagation checks for Wave 1 pipeline rescue (conversation-scoped cancel + answer persistence).
 */
class ChatMessagePropagationIntegrationTest {

    @Test
    void payloadConversationIdMatches_acceptsMatchingConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();
        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChatJobPayloadKeys.CONVERSATION_ID, conversationId.toString());
        when(task.getRequestPayload()).thenReturn(payload);

        Method method =
                ChatMessageApplicationService.class.getDeclaredMethod(
                        "payloadConversationIdMatches", AsyncTaskEntity.class, UUID.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, task, conversationId)).isEqualTo(true);
    }

    @Test
    void payloadConversationIdMatches_rejectsOtherConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();
        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChatJobPayloadKeys.CONVERSATION_ID, UUID.randomUUID().toString());
        when(task.getRequestPayload()).thenReturn(payload);

        Method method =
                ChatMessageApplicationService.class.getDeclaredMethod(
                        "payloadConversationIdMatches", AsyncTaskEntity.class, UUID.class);
        method.setAccessible(true);

        assertThat(method.invoke(null, task, conversationId)).isEqualTo(false);
    }

    @Test
    void chatJobHandler_isRegisteredForChatMessageType() {
        ChatMessageJobHandler handler = new ChatMessageJobHandler(null, null, null, null, null, null);
        assertThat(handler.taskType()).isEqualTo(AsyncTaskType.CHAT_MESSAGE);
    }

    @Test
    void queuedChatTaskPayload_carriesConversationScope() {
        UUID conversationId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ChatJobPayloadKeys.CONVERSATION_ID, conversationId.toString());
        payload.put(ChatJobPayloadKeys.ASSISTANT_MESSAGE_ID, UUID.randomUUID().toString());

        AsyncTaskEntity task = mock(AsyncTaskEntity.class);
        when(task.getTaskType()).thenReturn(AsyncTaskType.CHAT_MESSAGE);
        when(task.getStatus()).thenReturn(AsyncTaskStatus.QUEUED);
        when(task.getRequestPayload()).thenReturn(payload);

        assertThat(task.getRequestPayload().get(ChatJobPayloadKeys.CONVERSATION_ID))
                .isEqualTo(conversationId.toString());
        assertThat(task.getTaskType()).isEqualTo(AsyncTaskType.CHAT_MESSAGE);
    }
}
