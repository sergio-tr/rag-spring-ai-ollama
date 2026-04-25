package com.uniovi.rag.application.port;

import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;

import java.util.UUID;

/**
 * Persistence for {@code conversations.pending_clarification_jsonb}. Mutators use {@code REQUIRES_NEW}.
 */
public interface PendingClarificationStore {

    PendingClarificationLoad load(UUID conversationId);

    void saveReplace(UUID conversationId, PendingClarificationState state);

    void clear(UUID conversationId);
}
