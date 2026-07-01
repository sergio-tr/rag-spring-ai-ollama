package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagRankerDecisionPolicyTest {

    @Test
    void skipsClearFactualActaQueryWithGoodRetrieval() {
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.normalizedQueryText()).thenReturn("en qué acta se habló sobre terrazas");
        List<RetrievalCandidate> candidates =
                List.of(candidate("a", 0.8), candidate("b", 0.7));

        assertThat(RagRankerDecisionPolicy.decide("q", plan, candidates))
                .isEqualTo(RagRankerDecisionPolicy.Decision.SKIP_CLEAR_FACTUAL);
    }

    @Test
    void runsWhenRetrievalScoreLow() {
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.normalizedQueryText()).thenReturn("explain the governance model for terrace usage");
        List<RetrievalCandidate> candidates = List.of(candidate("a", 0.1), candidate("b", 0.2));

        assertThat(RagRankerDecisionPolicy.decide("q", plan, candidates))
                .isEqualTo(RagRankerDecisionPolicy.Decision.RUN);
    }

    private static RetrievalCandidate candidate(String id, double score) {
        return new RetrievalCandidate(
                id, "content", Map.of(), score, 0.5, 1, 1, UUID.randomUUID(), score);
    }
}
