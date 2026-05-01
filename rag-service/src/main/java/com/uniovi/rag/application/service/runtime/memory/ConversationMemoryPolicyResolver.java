package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryMode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Pure policy resolver for the memory stage (P12).
 */
@Service
public class ConversationMemoryPolicyResolver {

    public ConversationMemoryDecision resolve(ExecutionContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.memoryEnabled()) {
            return new ConversationMemoryDecision(
                    ConversationMemoryMode.DISABLED,
                    false,
                    false,
                    ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                    List.of("memoryEnabled=false"));
        }
        if (ctx.conversationId() == null) {
            return new ConversationMemoryDecision(
                    ConversationMemoryMode.DISABLED,
                    false,
                    false,
                    ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                    List.of("no_conversation_scope"));
        }
        return new ConversationMemoryDecision(
                ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                true,
                true,
                ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                List.of("enabled"));
    }
}

