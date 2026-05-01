package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Extractive compression: drop lowest-scoring candidates (by rerank order tail) until under budget.
 */
@Service
public class ContextCompressionStrategy {

    private static final String TRUNC_MARKER = "\n...[context truncated]\n";

    public CompressionResult compress(List<RetrievalCandidate> candidates, int maxContextChars) {
        List<String> rules = new ArrayList<>();
        int charsBefore = totalChars(candidates);
        if (charsBefore <= maxContextChars) {
            return new CompressionResult(
                    candidates,
                    new CompressionOutcome(charsBefore, charsBefore, 0, rules));
        }
        List<RetrievalCandidate> kept = new ArrayList<>(candidates);
        int dropped = 0;
        while (totalChars(kept) > maxContextChars && kept.size() > 1) {
            kept.remove(kept.size() - 1);
            dropped++;
        }
        rules.add("drop_lowest_rerank_tail");
        int after = totalChars(kept);
        if (after > maxContextChars && !kept.isEmpty()) {
            RetrievalCandidate last = kept.get(kept.size() - 1);
            String content = last.content() != null ? last.content() : "";
            int budget = Math.max(0, maxContextChars - (after - content.length()));
            String truncated = truncate(content, budget);
            kept.set(
                    kept.size() - 1,
                    new RetrievalCandidate(
                            last.candidateId(),
                            truncated + TRUNC_MARKER,
                            last.metadata(),
                            last.denseScore(),
                            last.sparseScore(),
                            last.denseRank(),
                            last.sparseRank(),
                            last.snapshotId(),
                            last.fusedRrfScore()));
            rules.add("truncate_last_chunk");
            after = totalChars(kept);
        }
        return new CompressionResult(
                kept,
                new CompressionOutcome(charsBefore, after, dropped, List.copyOf(rules)));
    }

    private static int totalChars(List<RetrievalCandidate> candidates) {
        int n = 0;
        for (RetrievalCandidate c : candidates) {
            if (c.content() != null) {
                n += c.content().length();
            }
        }
        return n;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content != null ? content : "";
        }
        return content.substring(0, Math.max(0, maxChars));
    }

    public record CompressionResult(List<RetrievalCandidate> candidates, CompressionOutcome outcome) {}
}
