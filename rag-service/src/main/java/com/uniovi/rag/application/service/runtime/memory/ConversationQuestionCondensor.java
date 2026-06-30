package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Single-call, bounded, LLM-backed condensation for conversational planning input (P12).
 * This component does not call tools/advisors/FC and has no retries; failures are handled by the caller.
 */
@Service
public class ConversationQuestionCondensor {

    private final ChatClient chatClient;
    private final ChatGenerationModelSelector chatGenerationModelSelector;

    public ConversationQuestionCondensor(
            ChatClient chatClient, ChatGenerationModelSelector chatGenerationModelSelector) {
        this.chatClient = chatClient;
        this.chatGenerationModelSelector = chatGenerationModelSelector;
    }

    public String condense(
            ExecutionContext ctx,
            ConversationMemorySlice slice,
            String literalLatestUserTurn,
            String preMemoryPlanningInputText) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(slice, "slice");

        Optional<String> deterministic =
                ConversationFollowUpResolver.expand(slice.turns(), literalLatestUserTurn);
        if (deterministic.isPresent() && !deterministic.get().isBlank()) {
            return deterministic.get();
        }

        String prompt = buildUserPrompt(slice, literalLatestUserTurn, preMemoryPlanningInputText);
        var spec = chatClient.prompt()
                .system(ConversationCondensePromptSources.SYSTEM_PROMPT)
                .user(prompt);

        OllamaOptions.Builder opt = OllamaOptions.builder().temperature(0.0);
        chatGenerationModelSelector.effectiveChatModelId(ctx).ifPresent(opt::model);
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

        return ConversationCondensePromptSources.USER_PROMPT_WRAPPER.formatted(
                history.toString().trim(), safe(literalLatestUserTurn), safe(preMemoryPlanningInputText));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

