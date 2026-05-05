package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Snapshot-bound dense retrieval via pgvector similarity search and post-filtering.
 */
@Service
public class DenseRetrievalStrategy {

    private final PgVectorStore vectorStore;
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public DenseRetrievalStrategy(
            PgVectorStore vectorStore,
            @Value("${spring.ai.ollama.top-k:10}") int defaultTopK,
            @Value("${spring.ai.ollama.similarity-threshold:0.7}")
                    double defaultSimilarityThreshold) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    public List<RetrievalCandidate> retrieve(RetrievalRequest req) {
        double sim = effectiveSimilarityThreshold();
        SearchRequest searchReq =
                SearchRequest.builder().query(req.queryText()).topK(req.denseFetchLimit()).similarityThreshold(sim).build();
        List<Document> raw = vectorStore.similaritySearch(searchReq);
        Set<String> allowed = req.snapshotIds().stream().map(UUID::toString).collect(Collectors.toSet());
        List<Document> filtered = new ArrayList<>();
        for (Document d : raw) {
            if (snapshotMatches(d, allowed)) {
                filtered.add(d);
            }
        }
        filtered = applyProjectAndDocumentFilter(filtered);
        int rank = 1;
        List<RetrievalCandidate> out = new ArrayList<>();
        for (Document d : filtered) {
            UUID sid = parseSnapshotId(d);
            if (sid == null) {
                continue;
            }
            String cid = RetrievalCandidateIds.fromDocument(d, sid);
            double denseScore = extractDenseScore(d);
            double rrfProxy = 1.0 / (RetrievalPolicy.RRF_K + rank);
            Map<String, Object> meta =
                    d.getMetadata() != null ? Map.copyOf(d.getMetadata()) : Map.of();
            out.add(
                    new RetrievalCandidate(
                            cid,
                            d.getText() != null ? d.getText() : "",
                            meta,
                            denseScore,
                            Double.NaN,
                            rank,
                            0,
                            sid,
                            rrfProxy));
            rank++;
            if (out.size() >= req.topKDense()) {
                break;
            }
        }
        return out;
    }

    private static UUID parseSnapshotId(Document d) {
        Map<String, Object> meta = d.getMetadata();
        Object sid = meta.get("indexSnapshotId");
        if (sid == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(sid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean snapshotMatches(Document d, Set<String> allowedSnapshotIdStrings) {
        Map<String, Object> meta = d.getMetadata();
        Object sid = meta.get("indexSnapshotId");
        if (sid == null) {
            return false;
        }
        return allowedSnapshotIdStrings.contains(String.valueOf(sid));
    }

    private List<Document> applyProjectAndDocumentFilter(List<Document> docs) {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx == null || !ctx.restrictsByProject()) {
            return docs;
        }
        String pid = ctx.projectId();
        List<Document> byProject = docs.stream().filter(d -> passesProjectMetadata(d, pid, ctx)).toList();
        if (ctx.documentFilterIsAll()) {
            return byProject;
        }
        Set<String> allowed = new HashSet<>();
        for (String id : ctx.documentFilter()) {
            if (id != null && !id.isBlank() && !RagExecutionContext.ALL_DOCUMENTS.equalsIgnoreCase(id.trim())) {
                allowed.add(id.trim());
            }
        }
        return byProject.stream().filter(d -> passesDocumentAllowlist(d, allowed)).toList();
    }

    private static boolean passesProjectMetadata(Document d, String projectId, RagExecutionContext ctx) {
        Map<String, Object> meta = d.getMetadata();
        Object cs = meta.get("corpusScope");
        if ("CHAT_LOCAL".equalsIgnoreCase(String.valueOf(cs))) {
            if (ctx.conversationId() == null) {
                return false;
            }
            Object conv = meta.get("conversationId");
            return ctx.conversationId().equals(String.valueOf(conv));
        }
        Object p = meta.get("projectId");
        if (p == null) {
            // Project-scoped chat must not include chunks with missing project metadata.
            return false;
        }
        return projectId.equals(String.valueOf(p));
    }

    private static boolean passesDocumentAllowlist(Document d, Set<String> allowed) {
        Map<String, Object> meta = d.getMetadata();
        Object id = meta.get("document_id");
        if (id == null) {
            id = meta.get("documentId");
        }
        if (id == null) {
            id = meta.get("projectDocumentId");
        }
        if (id == null) {
            return false;
        }
        return allowed.contains(String.valueOf(id));
    }

    private double effectiveSimilarityThreshold() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null && ctx.resolvedConfig().similarityThreshold() > 0) {
            return ctx.resolvedConfig().similarityThreshold();
        }
        return defaultSimilarityThreshold;
    }

    private static double extractDenseScore(Document d) {
        Map<String, Object> meta = d.getMetadata();
        Object dist = meta.get("distance");
        if (dist instanceof Number n) {
            return n.doubleValue();
        }
        Object score = meta.get("score");
        if (score instanceof Number n) {
            return n.doubleValue();
        }
        return Double.NaN;
    }
}
