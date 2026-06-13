package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reciprocal Rank Fusion (RRF) with fixed {@link RetrievalPolicy#RRF_K}.
 */
@Service
public class RetrievalFusionService {

    public RetrievedContextSet fuse(RetrievalRequest req, List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
        Set<String> denseIds = candidateIds(dense);
        Set<String> sparseIds = candidateIds(sparse);
        FusionOriginSupport.OriginCounts origins = FusionOriginSupport.countOrigins(dense, sparse);

        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < dense.size(); i++) {
            String id = dense.get(i).candidateId();
            scores.merge(id, 1.0 / (RetrievalPolicy.RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < sparse.size(); i++) {
            String id = sparse.get(i).candidateId();
            scores.merge(id, 1.0 / (RetrievalPolicy.RRF_K + i + 1), Double::sum);
        }

        Map<String, RetrievalCandidate> byId = new HashMap<>();
        for (RetrievalCandidate c : dense) {
            byId.putIfAbsent(
                    c.candidateId(),
                    FusionOriginSupport.tagOrigin(c, originLabel(c.candidateId(), denseIds, sparseIds)));
        }
        for (RetrievalCandidate c : sparse) {
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
                        dense.size(), sparse.size(), fused.size(), origins);

        return new RetrievedContextSet(
                fused,
                Optional.of(RetrievalFusionMode.RRF_ONLY),
                dense.size(),
                sparse.size(),
                fused.size(),
                origins.denseOnly(),
                origins.sparseOnly(),
                origins.both(),
                originsSummary);
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
}
