package com.uniovi.rag.service.ranker;

import com.uniovi.rag.application.model.CandidateResponse;
import com.uniovi.rag.domain.model.RankerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaithfulnessRankerTest {

    private ChatClient chatClient;
    private FaithfulnessRanker ranker;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ranker = new FaithfulnessRanker(chatClient);
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
        verifyNoInteractions(chatClient);
    }
}
