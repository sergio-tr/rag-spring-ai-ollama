package com.uniovi.rag.services.ranker;

import com.uniovi.rag.model.CandidateResponse;
import com.uniovi.rag.model.RankerResult;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses a single LLM call to judge which candidate response is best (or to rank them).
 */
public class LLMAsJudgeRanker implements ResponseRanker {

    private static final String PROMPT_TEMPLATE = """
        Question: %s

        Context (excerpt): %s

        Candidate responses (numbered 1 to N):
        %s

        Which candidate best answers the question based on the context? Reply with only the number (e.g. 1 or 2).
        """;

    private final ChatClient chatClient;

    public LLMAsJudgeRanker(ChatClient chatClient) {
        this.chatClient = chatClient;
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
        String prompt = String.format(PROMPT_TEMPLATE, query, contextExcerpt, numbered);
        try {
            String answer = chatClient.prompt().user(prompt).call().content();
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
}
