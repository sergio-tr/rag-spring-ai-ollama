package com.uniovi.rag.service.async.chat;

/**
 * Keys in {@code async_task.request_payload} for {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE}.
 */
public final class ChatJobPayloadKeys {

    private ChatJobPayloadKeys() {}

    public static final String CONVERSATION_ID = "conversationId";
    public static final String USER_MESSAGE_ID = "userMessageId";
    public static final String ASSISTANT_MESSAGE_ID = "assistantMessageId";
    public static final String LLM_MODEL = "llmModel";
    /** User message text (job input; avoids stale read if user edited). */
    public static final String USER_TEXT = "userText";
    /** Project document UUID strings for retrieval (snapshot at enqueue). */
    public static final String DOCUMENT_FILTER = "documentFilter";
}
