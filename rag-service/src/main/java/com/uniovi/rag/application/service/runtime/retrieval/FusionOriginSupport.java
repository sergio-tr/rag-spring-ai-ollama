package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes dense/sparse/both origin counts and summary strings for hybrid fusion telemetry. */
public final class FusionOriginSupport {

    private FusionOriginSupport() {}

    public record OriginCounts(int denseOnly, int sparseOnly, int both) {}

    public static OriginCounts countOrigins(List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
        Set<String> denseIds = ids(dense);
        Set<String> sparseIds = ids(sparse);
        int both = 0;
        for (String id : denseIds) {
            if (sparseIds.contains(id)) {
                both++;
            }
        }
        int denseOnly = denseIds.size() - both;
        int sparseOnly = sparseIds.size() - both;
        return new OriginCounts(denseOnly, sparseOnly, both);
    }

    public static String formatCandidateOrigins(
            int denseInput, int sparseInput, int fused, OriginCounts origins) {
        StringBuilder sb = new StringBuilder();
        if (denseInput >= 0) {
            sb.append("dense=").append(denseInput);
        }
        if (sparseInput >= 0) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append("sparse=").append(sparseInput);
        }
        if (origins != null && origins.both() >= 0) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append("both=").append(origins.both());
        }
        if (fused >= 0) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append("fused=").append(fused);
        }
        return sb.toString();
    }

    public static Map<String, Object> withRetrievalOrigin(Map<String, Object> metadata, String origin) {
        Map<String, Object> copy = new HashMap<>(metadata != null ? metadata : Map.of());
        if (origin != null && !origin.isBlank()) {
            copy.put("retrievalOrigin", origin);
        }
        return Map.copyOf(copy);
    }

    public static RetrievalCandidate tagOrigin(RetrievalCandidate candidate, String origin) {
        return new RetrievalCandidate(
                candidate.candidateId(),
                candidate.content(),
                withRetrievalOrigin(candidate.metadata(), origin),
                candidate.denseScore(),
                candidate.sparseScore(),
                candidate.denseRank(),
                candidate.sparseRank(),
                candidate.snapshotId(),
                candidate.fusedRrfScore());
    }

    private static Set<String> ids(List<RetrievalCandidate> candidates) {
        Set<String> out = new HashSet<>();
        if (candidates == null) {
            return out;
        }
        for (RetrievalCandidate c : candidates) {
            if (c != null && c.candidateId() != null) {
                out.add(c.candidateId());
            }
        }
        return out;
    }
}
