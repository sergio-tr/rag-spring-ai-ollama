package com.uniovi.rag.application.service.runtime.ranking;

import com.uniovi.rag.application.result.query.CandidateResponse;
import com.uniovi.rag.domain.model.RankerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FaithfulnessRankerTest {

    private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private FaithfulnessRanker ranker;

    @BeforeEach
    void setUp() {
        secondaryLlmExecutor = mock(ProviderAwareSecondaryLlmExecutor.class);
        ranker = new FaithfulnessRanker(secondaryLlmExecutor);
    }

    @Test
    void selectBest_nullCandidates_returnsNull() {
        assertNull(ranker.selectBest("q", "ctx", null));
        assertNull(ranker.selectBest("q", "ctx", List.of()));
    }

    @Test
    void selectBest_singleCandidate_returnsItWithoutCallingLlm() {
        List<CandidateResponse> candidates = List.of(CandidateResponse.of("only one"));
        RankerResult result = ranker.selectBest("query", "context", candidates);
        assertNotNull(result);
        assertEquals("only one", result.chosenText());
        assertEquals(0, result.chosenIndex());
        assertEquals(List.of(1.0), result.scoresPerCandidate());
        verifyNoInteractions(secondaryLlmExecutor);
    }
}
