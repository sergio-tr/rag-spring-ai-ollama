package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.application.config.ConfigurablePromptRuntimeSupport;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves configurable answer-synthesis, grounding, and abstention prompts at runtime. */
@Service
public class RuntimeAnswerPromptResolver {

    private final ConfigurablePromptResolver promptResolver;

    public RuntimeAnswerPromptResolver(ConfigurablePromptResolver promptResolver) {
        this.promptResolver = promptResolver;
    }

    public String ragUserTurn(
            ExecutionContext ctx,
            String rawQuestion,
            String contextBlock,
            AnswerGroundingPolicy policy,
            boolean documentScopedQuestion,
            Optional<String> dateMismatchNotice,
            String answerPlanBlock) {
        String synthesis =
                ConfigurablePromptRuntimeSupport.resolve(
                        promptResolver, ConfigurablePromptGroup.ANSWER_SYNTHESIS, ctx);
        String grounding =
                ConfigurablePromptRuntimeSupport.resolve(
                        promptResolver, ConfigurablePromptGroup.SOURCE_GROUNDING, ctx);
        return RuntimeAnswerPrompts.ragUserTurn(
                rawQuestion,
                contextBlock,
                policy,
                documentScopedQuestion,
                dateMismatchNotice,
                answerPlanBlock,
                synthesis,
                grounding);
    }

    public String insufficientDocumentContextMessage(ExecutionContext ctx, String rawQuestion) {
        String resolved =
                ConfigurablePromptRuntimeSupport.resolve(
                        promptResolver, ConfigurablePromptGroup.ABSTENTION, ctx);
        if (resolved != null && !resolved.isBlank()) {
            return resolved.trim();
        }
        return RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(rawQuestion);
    }
}
