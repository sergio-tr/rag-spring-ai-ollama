package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reciprocal Rank Fusion (RRF) with fixed {@link RetrievalPolicy#RRF_K}.
 */
@Service
public class RetrievalFusionService {

    public RetrievedContextSet fuse(RetrievalRequest req, List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse) {
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
            byId.putIfAbsent(c.candidateId(), c);
        }
        for (RetrievalCandidate c : sparse) {
            byId.merge(
                    c.candidateId(),
                    c,
                    (a, b) -> preferRicherContent(a, b));
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

        return new RetrievedContextSet(
                fused,
                Optional.of(RetrievalFusionMode.RRF_ONLY),
                dense.size(),
                sparse.size(),
                fused.size());
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
