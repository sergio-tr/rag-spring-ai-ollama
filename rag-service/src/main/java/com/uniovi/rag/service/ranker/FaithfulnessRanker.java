package com.uniovi.rag.service.ranker;

import com.uniovi.rag.model.CandidateResponse;
import com.uniovi.rag.model.RankerResult;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores each candidate by asking the LLM whether it is supported by the context; returns highest-scoring.
 */
public class FaithfulnessRanker implements ResponseRanker {

    private static final String PROMPT = """
        Context: %s
        Response: %s
        Is this response fully supported by the context? Answer only: Yes or No.
        """;

    private final ChatClient chatClient;

    public FaithfulnessRanker(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public RankerResult selectBest(String query, String context, List<CandidateResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return RankerResult.of(candidates.get(0).text(), 0, List.of(1.0));
        }
        String contextExcerpt = context != null && context.length() > 600 ? context.substring(0, 600) + "..." : (context != null ? context : "");
        List<Double> scores = new ArrayList<>();
        for (CandidateResponse c : candidates) {
            double score = scoreCandidate(contextExcerpt, c.text());
            scores.add(score);
        }
        int best = 0;
        for (int i = 1; i < scores.size(); i++) {
            if (scores.get(i) > scores.get(best)) {
                best = i;
            }
        }
        return RankerResult.of(candidates.get(best).text(), best, scores);
    }

    private double scoreCandidate(String context, String response) {
        try {
            String prompt = String.format(PROMPT, context, response);
            String answer = chatClient.prompt().user(prompt).call().content();
            return (answer != null && answer.trim().toLowerCase().startsWith("yes")) ? 1.0 : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
