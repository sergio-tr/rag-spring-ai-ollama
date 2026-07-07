package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decides when deterministic or LLM ranker work can be skipped for clear factual acta queries. */
public final class RagRankerDecisionPolicy {

    private static final Logger log = LoggerFactory.getLogger(RagRankerDecisionPolicy.class);

    public enum Decision {
        RUN,
        SKIP_CLEAR_FACTUAL
    }

    private static final double MIN_TOP_SCORE = 0.45;
    private static final int MIN_CANDIDATES = 2;

    private RagRankerDecisionPolicy() {}

    public static Decision decide(String query, QueryPlan plan, List<RetrievalCandidate> candidates) {
        String normalized = plan != null && plan.normalizedQueryText() != null
                ? plan.normalizedQueryText()
                : query;
        if (!RagLlmCallBudgetPolicy.isClearFactualQuery(normalized)) {
            return Decision.RUN;
        }
        if (DeterministicQueryRewriteShortcuts.matches(normalized).isPresent()) {
            return logAndReturn(Decision.SKIP_CLEAR_FACTUAL, "deterministic_rewrite_matched");
        }
        if (candidates == null || candidates.size() < MIN_CANDIDATES) {
            return Decision.RUN;
        }
        double topScore = topRetrievalScore(candidates);
        if (topScore < MIN_TOP_SCORE) {
            return Decision.RUN;
        }
        if (plan != null
                && plan.ambiguityAssessment() != null
                && plan.ambiguityAssessment().status() != AmbiguityStatus.SUFFICIENT) {
            return Decision.RUN;
        }
        return logAndReturn(Decision.SKIP_CLEAR_FACTUAL, "clear_factual_good_retrieval");
    }

    private static double topRetrievalScore(List<RetrievalCandidate> candidates) {
        double best = 0.0;
        for (RetrievalCandidate c : candidates) {
            if (c == null) {
                continue;
            }
            double dense = c.denseScore();
            double fused = c.fusedRrfScore();
            double score = Math.max(dense, fused);
            if (Double.isFinite(score)) {
                best = Math.max(best, score);
            }
        }
        return best;
    }

    private static Decision logAndReturn(Decision decision, String reason) {
        log.info("RAG_RANKER_DECISION decision={} reason={}", decision, reason);
        return decision;
    }
}
