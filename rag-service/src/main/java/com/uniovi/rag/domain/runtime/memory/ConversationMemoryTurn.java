package com.uniovi.rag.domain.runtime.memory;

import com.uniovi.rag.domain.MessageRole;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable projection of one persisted conversation message (P12).
 */
public record ConversationMemoryTurn(
        UUID id,
        int seq,
        MessageRole role,
        String content) {

    public ConversationMemoryTurn {
        id = Objects.requireNonNull(id, "id");
        role = Objects.requireNonNull(role, "role");
        content = content != null ? content : "";
        if (seq < 0) {
            throw new IllegalArgumentException("seq must be >= 0");
        }
    }
}

