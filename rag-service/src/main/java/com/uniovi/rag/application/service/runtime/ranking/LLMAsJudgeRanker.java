package com.uniovi.rag.application.service.runtime.ranking;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.application.result.query.CandidateResponse;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.model.RankerResult;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses a single LLM call to judge which candidate response is best (or to rank them).
 */
public class LLMAsJudgeRanker implements ResponseRanker {

    static final String OPERATION_LLM_RANKER = "llm-ranker";

    private static final String PROMPT_TEMPLATE = """
        Question: %s

        Context (excerpt): %s

        Candidate responses (numbered 1 to N):
        %s

        Which candidate best answers the question based on the context? Reply with only the number (e.g. 1 or 2).
        """;

    public static String defaultPromptTemplate() {
        return PROMPT_TEMPLATE;
    }

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private final ConfigurablePromptResolver promptResolver;

    public LLMAsJudgeRanker(
            ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor, ConfigurablePromptResolver promptResolver) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
        this.promptResolver = promptResolver;
    }

    @Override
    public RankerResult selectBest(String query, String context, List<CandidateResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return RankerResult.of(candidates.get(0).text(), 0);
        }
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            numbered.append(i + 1).append(". ").append(candidates.get(i).text()).append("\n\n");
        }
        String contextExcerpt = context != null && context.length() > 800 ? context.substring(0, 800) + "..." : (context != null ? context : "");
        String template = resolveRankerTemplate();
        String prompt = String.format(template, query, contextExcerpt, numbered);
        try {
            String answer = secondaryLlmExecutor.complete(OPERATION_LLM_RANKER, null, prompt);
            int index = parseChosenIndex(answer, candidates.size());
            if (index >= 0 && index < candidates.size()) {
                return RankerResult.of(candidates.get(index).text(), index);
            }
        } catch (Exception ignored) {
        }
        return RankerResult.of(candidates.get(0).text(), 0);
    }

    private int parseChosenIndex(String answer, int maxIndex) {
        if (answer == null) return 0;
        Matcher m = Pattern.compile("\\b([1-9]\\d*)\\b").matcher(answer.trim());
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return n >= 1 && n <= maxIndex ? n - 1 : 0;
        }
        return 0;
    }

    private String resolveRankerTemplate() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.userId() != null && !ctx.userId().isBlank()) {
            UUID userId = UUID.fromString(ctx.userId());
            UUID projectId =
                    ctx.projectId() != null && !ctx.projectId().isBlank()
                            ? UUID.fromString(ctx.projectId())
                            : null;
            return promptResolver.resolve(ConfigurablePromptGroup.LLM_RANKER, userId, projectId);
        }
        return PROMPT_TEMPLATE;
    }
}
