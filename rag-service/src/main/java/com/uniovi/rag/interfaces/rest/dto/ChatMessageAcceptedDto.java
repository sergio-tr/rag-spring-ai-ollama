package com.uniovi.rag.interfaces.rest.dto;

import java.util.UUID;

/** HTTP 202 body after enqueueing {@link com.uniovi.rag.domain.AsyncTaskType#CHAT_MESSAGE}. */
public record ChatMessageAcceptedDto(UUID jobId, UUID userMessageId, UUID assistantMessageId) {}
