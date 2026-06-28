package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.FusionTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Reciprocal Rank Fusion (RRF) with fixed {@link RetrievalPolicy#RRF_K}.
 */
@Service
public class RetrievalFusionService {

    public record FusionResult(RetrievedContextSet retrieved, FusionTelemetry telemetry) {}

    public RetrievedContextSet fuse(RetrievalRequest req, List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
        return fuseWithTelemetry(req, dense, sparse).retrieved();
    }

    public FusionResult fuseWithTelemetry(
            RetrievalRequest req, List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
        List<RetrievalCandidate> safeDense = dense != null ? dense : List.of();
        List<RetrievalCandidate> safeSparse = sparse != null ? sparse : List.of();
        int preFusion = safeDense.size() + safeSparse.size();

        if (safeSparse.isEmpty()) {
            RetrievedContextSet denseOnly = denseOnlyPassthrough(req, safeDense);
            FusionTelemetry telemetry =
                    new FusionTelemetry("DENSE_ONLY_FALLBACK", preFusion, denseOnly.fusedCount(), 0, false);
            return new FusionResult(denseOnly, telemetry);
        }
        if (safeDense.isEmpty()) {
            RetrievedContextSet sparseOnly = sparseOnlyPassthrough(req, safeSparse);
            FusionTelemetry telemetry =
                    new FusionTelemetry("SPARSE_ONLY", preFusion, sparseOnly.fusedCount(), 0, false);
            return new FusionResult(sparseOnly, telemetry);
        }

        Set<String> denseIds = candidateIds(safeDense);
        Set<String> sparseIds = candidateIds(safeSparse);
        FusionOriginSupport.OriginCounts origins = FusionOriginSupport.countOrigins(safeDense, safeSparse);

        Map<String, Double> scores = new HashMap<>();
        final double denseLegWeight = sparseWouldDominateDense(safeDense, safeSparse) ? 1.18 : 1.0;
        for (int i = 0; i < safeDense.size(); i++) {
            String id = safeDense.get(i).candidateId();
            scores.merge(id, denseLegWeight / (RetrievalPolicy.RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < safeSparse.size(); i++) {
            String id = safeSparse.get(i).candidateId();
            scores.merge(id, 1.0 / (RetrievalPolicy.RRF_K + i + 1), Double::sum);
        }

        Map<String, RetrievalCandidate> byId = new HashMap<>();
        for (RetrievalCandidate c : safeDense) {
            byId.putIfAbsent(
                    c.candidateId(),
                    FusionOriginSupport.tagOrigin(c, originLabel(c.candidateId(), denseIds, sparseIds)));
        }
        for (RetrievalCandidate c : safeSparse) {
            byId.merge(
                    c.candidateId(),
                    FusionOriginSupport.tagOrigin(c, originLabel(c.candidateId(), denseIds, sparseIds)),
                    RetrievalFusionService::preferRicherContent);
        }

        List<String> orderedIds =
                scores.entrySet().stream()
                        .sorted(
                                Comparator.<Map.Entry<String, Double>, Double>comparing(Map.Entry::getValue)
                                        .reversed()
                                        .thenComparing(Map.Entry::getKey))
                        .map(Map.Entry::getKey)
                        .toList();

        List<RetrievalCandidate> fused = new ArrayList<>();
        for (String id : orderedIds) {
            if (fused.size() >= req.fusionOutputCap()) {
                break;
            }
            RetrievalCandidate base = byId.get(id);
            if (base == null) {
                continue;
            }
            double rrf = scores.getOrDefault(id, 0.0);
            fused.add(
                    new RetrievalCandidate(
                            base.candidateId(),
                            base.content(),
                            base.metadata(),
                            base.denseScore(),
                            base.sparseScore(),
                            base.denseRank(),
                            base.sparseRank(),
                            base.snapshotId(),
                            rrf));
        }

        String originsSummary =
                FusionOriginSupport.formatCandidateOrigins(
                        safeDense.size(), safeSparse.size(), fused.size(), origins);

        RetrievedContextSet retrieved =
                new RetrievedContextSet(
                        fused,
                        Optional.of(RetrievalFusionMode.RRF_ONLY),
                        safeDense.size(),
                        safeSparse.size(),
                        fused.size(),
                        origins.denseOnly(),
                        origins.sparseOnly(),
                        origins.both(),
                        originsSummary);

        boolean hybridApplied =
                !safeSparse.isEmpty() && !safeDense.isEmpty() && fused.size() > 0 && origins.hasBothLegs();
        FusionTelemetry telemetry =
                new FusionTelemetry("RRF", preFusion, fused.size(), 0, hybridApplied);
        return new FusionResult(retrieved, telemetry);
    }

    private static RetrievedContextSet denseOnlyPassthrough(RetrievalRequest req, List<RetrievalCandidate> dense) {
        List<RetrievalCandidate> capped = new ArrayList<>();
        for (RetrievalCandidate c : dense) {
            if (capped.size() >= req.fusionOutputCap()) {
                break;
            }
            capped.add(FusionOriginSupport.tagOrigin(c, "DENSE"));
        }
        String origins =
                FusionOriginSupport.formatCandidateOrigins(
                        dense.size(), 0, capped.size(), new FusionOriginSupport.OriginCounts(dense.size(), 0, 0));
        return new RetrievedContextSet(
                capped,
                Optional.empty(),
                dense.size(),
                0,
                capped.size(),
                dense.size(),
                0,
                0,
                origins);
    }

    private static RetrievedContextSet sparseOnlyPassthrough(RetrievalRequest req, List<RetrievalCandidate> sparse) {
        List<RetrievalCandidate> capped = new ArrayList<>();
        for (RetrievalCandidate c : sparse) {
            if (capped.size() >= req.fusionOutputCap()) {
                break;
            }
            capped.add(FusionOriginSupport.tagOrigin(c, "SPARSE"));
        }
        String origins =
                FusionOriginSupport.formatCandidateOrigins(
                        0, sparse.size(), capped.size(), new FusionOriginSupport.OriginCounts(0, sparse.size(), 0));
        return new RetrievedContextSet(
                capped,
                Optional.of(RetrievalFusionMode.RRF_ONLY),
                0,
                sparse.size(),
                capped.size(),
                0,
                sparse.size(),
                0,
                origins);
    }

    private static String originLabel(String id, Set<String> denseIds, Set<String> sparseIds) {
        boolean inDense = denseIds.contains(id);
        boolean inSparse = sparseIds.contains(id);
        if (inDense && inSparse) {
            return "BOTH";
        }
        if (inSparse) {
            return "SPARSE";
        }
        return "DENSE";
    }

    private static Set<String> candidateIds(List<RetrievalCandidate> candidates) {
        Set<String> ids = new HashSet<>();
        if (candidates == null) {
            return ids;
        }
        for (RetrievalCandidate c : candidates) {
            if (c != null && c.candidateId() != null) {
                ids.add(c.candidateId());
            }
        }
        return ids;
    }

    private static RetrievalCandidate preferRicherContent(RetrievalCandidate a, RetrievalCandidate b) {
        int la = a.content() != null ? a.content().length() : 0;
        int lb = b.content() != null ? b.content().length() : 0;
        if (lb > la) {
            return b;
        }
        return a;
    }

    /**
     * When sparse leg introduces unrelated top ranks, slightly boost dense leg so relevant dense chunks are not suppressed.
     */
    private static boolean sparseWouldDominateDense(List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
        if (dense == null || sparse == null || dense.isEmpty() || sparse.isEmpty()) {
            return false;
        }
        Set<String> denseTop = new HashSet<>();
        for (int i = 0; i < Math.min(3, dense.size()); i++) {
            denseTop.add(dense.get(i).candidateId());
        }
        Set<String> sparseTop = new HashSet<>();
        for (int i = 0; i < Math.min(3, sparse.size()); i++) {
            sparseTop.add(sparse.get(i).candidateId());
        }
        if (sparseTop.isEmpty()) {
            return false;
        }
        long sparseOnlyTop =
                sparseTop.stream().filter(id -> !denseTop.contains(id)).count();
        return sparseOnlyTop >= 2;
    }
}
