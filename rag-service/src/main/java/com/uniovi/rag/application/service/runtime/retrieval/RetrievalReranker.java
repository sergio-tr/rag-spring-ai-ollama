package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RerankOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic composite reranking (no LLM).
 */
@Service
public class RetrievalReranker {

    private static final double W_RRF = 1000.0;
    private static final double W_ENTITY = 50.0;
    private static final double W_SLOT = 30.0;
    private static final double W_LOCALITY = 10.0;

    public RerankResult rerank(RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        int windowMin = computeLocalityWindowMin(candidates, 10);
        List<Scored> scored = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            double entityOverlap = entityOverlap(plan.entityExtractionResult(), plan.targetEntities(), c);
            double slotMatch = slotMatch(plan, c);
            double locality = localityBonus(c, windowMin);
            double s = W_RRF * c.fusedRrfScore() + W_ENTITY * entityOverlap + W_SLOT * slotMatch + W_LOCALITY * locality;
            scored.add(new Scored(c, s));
        }
        scored.sort(
                Comparator.<Scored, Double>comparing(Scored::score)
                        .reversed()
                        .thenComparing(
                                s -> Double.isFinite(s.candidate().denseScore()) ? s.candidate().denseScore() : -1.0,
                                Comparator.reverseOrder())
                        .thenComparing(s -> s.candidate().candidateId()));

        List<RetrievalCandidate> ordered = new ArrayList<>();
        List<RerankOutcome> outcomes = new ArrayList<>();
        int rank = 1;
        for (Scored s : scored) {
            if (ordered.size() >= req.postFusionCap()) {
                break;
            }
            ordered.add(s.candidate());
            outcomes.add(new RerankOutcome(s.candidate().candidateId(), s.score(), rank++));
        }
        return new RerankResult(ordered, outcomes);
    }

    private static int computeLocalityWindowMin(List<RetrievalCandidate> candidates, int head) {
        int n = Math.min(head, candidates.size());
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Integer idx = chunkIndex(candidates.get(i).metadata());
            if (idx != null) {
                min = Math.min(min, idx);
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static double localityBonus(RetrievalCandidate c, int windowMin) {
        Integer idx = chunkIndex(c.metadata());
        if (idx == null) {
            return 0.0;
        }
        return Math.abs(idx - windowMin) <= 2 ? 1.0 : 0.0;
    }

    private static Integer chunkIndex(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object v = meta.get("chunk_index");
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static double entityOverlap(
            EntityExtractionResult entities, List<String> targetEntities, RetrievalCandidate c) {
        String haystack = haystack(c);
        int hits = 0;
        for (String t : targetEntities) {
            if (t != null && containsIgnoreCase(haystack, t)) {
                hits++;
            }
        }
        for (String t : entities.people()) {
            if (t != null && containsIgnoreCase(haystack, t)) {
                hits++;
            }
        }
        for (String t : entities.organizations()) {
            if (t != null && containsIgnoreCase(haystack, t)) {
                hits++;
            }
        }
        for (String t : entities.locations()) {
            if (t != null && containsIgnoreCase(haystack, t)) {
                hits++;
            }
        }
        return hits;
    }

    private static String haystack(RetrievalCandidate c) {
        StringBuilder sb = new StringBuilder(c.content() != null ? c.content() : "");
        if (c.metadata() != null) {
            sb.append(' ').append(c.metadata().toString());
        }
        return sb.toString();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.trim().toLowerCase(Locale.ROOT));
    }

    private static double slotMatch(QueryPlan plan, RetrievalCandidate c) {
        if (plan.slots().isEmpty() || c.metadata() == null) {
            return 0.0;
        }
        int hits = 0;
        for (Map.Entry<String, String> e : plan.slots().entrySet()) {
            Object v = c.metadata().get(e.getKey());
            if (v != null && Objects.equals(String.valueOf(v), e.getValue())) {
                hits++;
            }
        }
        return hits;
    }

    private record Scored(RetrievalCandidate candidate, double score) {}

    public record RerankResult(List<RetrievalCandidate> candidates, List<RerankOutcome> outcomes) {}
}
