package com.uniovi.rag.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConversationRuntimeOverrideLoader;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Loads {@link ConversationEntity#getRuntimeOverride()} as JSON for resolution (read-only).
 */
@Service
public class ConversationRuntimeOverrideLoaderImpl implements ConversationRuntimeOverrideLoader {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    public ConversationRuntimeOverrideLoaderImpl(
            ConversationRepository conversationRepository, ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<JsonNode> loadRuntimeOverride(UUID userId, UUID conversationId) {
        if (userId == null || conversationId == null) {
            return Optional.empty();
        }
        return conversationRepository
                .findById(conversationId)
                .filter(c -> c.getUser() != null && userId.equals(c.getUser().getId()))
                .map(ConversationEntity::getRuntimeOverride)
                .filter(m -> m != null && !m.isEmpty())
                .map(objectMapper::valueToTree);
    }
}
