package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Single-call, bounded, LLM-backed condensation for conversational planning input (P12).
 * This component does not call tools/advisors/FC and has no retries; failures are handled by the caller.
 */
@Service
public class ConversationQuestionCondensor {

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private final ConfigurablePromptResolver promptResolver;

    public ConversationQuestionCondensor(
            ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor, ConfigurablePromptResolver promptResolver) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
        this.promptResolver = promptResolver;
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

        String prompt = buildUserPrompt(ctx, slice, literalLatestUserTurn, preMemoryPlanningInputText);
        String system =
                promptResolver.resolveSystem(ConfigurablePromptGroup.MEMORY_CONDENSE, ctx.userId(), ctx.projectId());
        return secondaryLlmExecutor
                .complete(
                        ctx,
                        "conversation-condense",
                        system,
                        prompt,
                        ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)
                .trim();
    }

    private String buildUserPrompt(
            ExecutionContext ctx,
            ConversationMemorySlice slice,
            String literalLatestUserTurn,
            String preMemoryPlanningInputText) {
        StringBuilder history = new StringBuilder();
        for (ConversationMemoryTurn t : slice.turns()) {
            history.append(t.role().name()).append(": ").append(t.content() == null ? "" : t.content()).append("\n");
        }

        String wrapper =
                promptResolver.resolve(ConfigurablePromptGroup.MEMORY_CONDENSE, ctx.userId(), ctx.projectId());
        return wrapper.formatted(
                history.toString().trim(), safe(literalLatestUserTurn), safe(preMemoryPlanningInputText));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

