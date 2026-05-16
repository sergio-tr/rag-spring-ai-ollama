package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Single-call, bounded, LLM-backed condensation for conversational planning input (P12).
 * This component does not call tools/advisors/FC and has no retries; failures are handled by the caller.
 */
@Service
public class ConversationQuestionCondensor {

    private final ChatClient chatClient;

    public ConversationQuestionCondensor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String condense(
            ExecutionContext ctx,
            ConversationMemorySlice slice,
            String literalLatestUserTurn,
            String preMemoryPlanningInputText) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(slice, "slice");

        String prompt = buildUserPrompt(slice, literalLatestUserTurn, preMemoryPlanningInputText);
        var spec = chatClient.prompt()
                .system("""
                        You are a deterministic query condenser for a multi-turn conversation.
                        Output ONLY a single plain text planning query. No markdown. No quotes.
                        Do not invent facts. Use only the provided history and the latest user turn.
                        """)
                .user(prompt);

        OllamaOptions.Builder opt = OllamaOptions.builder().temperature(0.0);
        ChatGenerationModelSelector.effectiveChatModelId(ctx).ifPresent(opt::model);
        spec = spec.options(opt.build());

        String out = spec.call().content();
        return out == null ? "" : out.trim();
    }

    private static String buildUserPrompt(
            ConversationMemorySlice slice,
            String literalLatestUserTurn,
            String preMemoryPlanningInputText) {
        StringBuilder history = new StringBuilder();
        for (ConversationMemoryTurn t : slice.turns()) {
            history.append(t.role().name()).append(": ").append(t.content() == null ? "" : t.content()).append("\n");
        }

        return """
                HISTORY (ordered oldest to newest, bounded):
                %s

                LATEST_USER_TURN (literal):
                %s

                PRE_MEMORY_PLANNING_INPUT (from clarification stage):
                %s

                TASK:
                Return one condensed planning query suitable for query understanding and downstream execution.
                """.formatted(history.toString().trim(), safe(literalLatestUserTurn), safe(preMemoryPlanningInputText));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

