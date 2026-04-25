package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic filtering after reranking.
 */
@Service
public class RetrievalFilter {

    public List<RetrievalCandidate> filter(RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        Set<UUID> allowedSnapshots = new HashSet<>(req.snapshotIds());
        final Set<String> allowedDocs;
        if (req.documentAllowlistIsAll()) {
            allowedDocs = null;
        } else {
            HashSet<String> tmp = new HashSet<>();
            for (String s : req.documentAllowlist()) {
                if (s != null && !s.isBlank() && !"all".equalsIgnoreCase(s.trim())) {
                    tmp.add(s.trim());
                }
            }
            allowedDocs = tmp;
        }
        return candidates.stream()
                .filter(c -> allowedSnapshots.contains(c.snapshotId()))
                .filter(c -> passesDocumentAllowlist(c, allowedDocs))
                .filter(c -> passesSlotConstraints(plan, c))
                .toList();
    }

    private static boolean passesDocumentAllowlist(RetrievalCandidate c, Set<String> allowedDocs) {
        if (allowedDocs == null) {
            return true;
        }
        String docId = extractDocumentId(c.metadata());
        return docId != null && allowedDocs.contains(docId);
    }

    private static String extractDocumentId(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object id = meta.get("document_id");
        if (id == null) {
            id = meta.get("documentId");
        }
        if (id == null) {
            id = meta.get("projectDocumentId");
        }
        return id != null ? String.valueOf(id) : null;
    }

    private static boolean passesSlotConstraints(QueryPlan plan, RetrievalCandidate c) {
        if (plan.slots().isEmpty() || c.metadata() == null) {
            return true;
        }
        for (Map.Entry<String, String> e : plan.slots().entrySet()) {
            if (!c.metadata().containsKey(e.getKey())) {
                continue;
            }
            Object v = c.metadata().get(e.getKey());
            if (!Objects.equals(String.valueOf(v), e.getValue())) {
                return false;
            }
        }
        return true;
    }
}
