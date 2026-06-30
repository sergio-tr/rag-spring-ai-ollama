package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.infrastructure.persistence.MessageRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MessageEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Loads persisted message history for the current conversation (P12).
 * Returns only immutable {@link ConversationMemoryTurn} projections.
 */
@Service
public class ConversationHistoryLoader {

    private final MessageRepository messageRepository;

    public ConversationHistoryLoader(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public List<ConversationMemoryTurn> loadEligibleHistory(ExecutionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        UUID conversationId = ctx.conversationId();
        if (conversationId == null) {
            return List.of();
        }

        List<MessageEntity> rows =
                messageRepository.findByConversation_IdAndDeletedAtIsNullOrderBySeqAsc(conversationId);

        UUID excludeId = ctx.originatingUserMessageId().orElse(null);
        List<ConversationMemoryTurn> out = new ArrayList<>();
        for (MessageEntity m : rows) {
            if (m == null) {
                continue;
            }
            if (excludeId != null && excludeId.equals(m.getId())) {
                continue;
            }
            MessageRole role = m.getRole();
            if (role != MessageRole.USER && role != MessageRole.ASSISTANT) {
                continue;
            }
            Map<String, Object> meta = m.getExecutionMetadata();
            out.add(
                    new ConversationMemoryTurn(
                            m.getId(),
                            m.getSeq(),
                            role,
                            m.getContent(),
                            meta != null ? meta : Map.of()));
        }
        return List.copyOf(out);
    }
}

