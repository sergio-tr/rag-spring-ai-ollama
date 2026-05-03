package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCompressionStrategyTest {

    private final ContextCompressionStrategy compression = new ContextCompressionStrategy();

    @Test
    void compress_dropsTailUntilUnderBudget() {
        UUID s = UUID.randomUUID();
        RetrievalCandidate a =
                new RetrievalCandidate(
                        s + ":a:0",
                        "a".repeat(100),
                        Map.of("indexSnapshotId", s.toString()),
                        1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        1);
        RetrievalCandidate b =
                new RetrievalCandidate(
                        s + ":b:0",
                        "b".repeat(100),
                        Map.of("indexSnapshotId", s.toString()),
                        1,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.5);

        var result = compression.compress(List.of(a, b), 120);

        assertThat(result.candidates().size()).isLessThanOrEqualTo(2);
        assertThat(result.outcome().charsAfter()).isLessThanOrEqualTo(120 + 500);
    }
}
