package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dense RAG retrieval constrained to {@link KnowledgeSnapshotSelection#orderedSnapshotIds()}. Sole gateway for
 * vector reads on document/chunk/metadata dense workflows.
 */
@Service
public class SnapshotBoundRetrievalService {

    /** One combined block per logical document (chunk grouping). */
    public enum DenseLayout {
        DOCUMENT_COMBINED,
        CHUNK_SEPARATE,
        CHUNK_SEPARATE_WITH_DB_METADATA
    }

    private final PgVectorStore vectorStore;
    private final SnapshotRetrievalFormattingHelper formattingHelper;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public SnapshotBoundRetrievalService(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            NamedParameterJdbcTemplate namedJdbc,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.top-k:10}") int defaultTopK,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.similarity-threshold:0.7}")
                    double defaultSimilarityThreshold,
            @org.springframework.beans.factory.annotation.Value("${knowledge.v2.chat-overlay.enabled:false}")
                    boolean knowledgeChatOverlayEnabled) {
        this.vectorStore = vectorStore;
        this.formattingHelper =
                new SnapshotRetrievalFormattingHelper(
                        vectorStore, chatClient, defaultTopK, defaultSimilarityThreshold, knowledgeChatOverlayEnabled);
        this.namedJdbc = namedJdbc;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    public String buildRetrievalContext(ExecutionContext ctx, String query, DenseLayout layout) {
        KnowledgeSnapshotSelection snap = ctx.knowledgeSnapshotSelection();
        List<UUID> snapshotIds = snap.orderedSnapshotIds();
        if (snapshotIds.isEmpty()) {
            throw new IllegalStateException("SnapshotBoundRetrievalService requires non-empty orderedSnapshotIds");
        }
        List<Document> raw =
                similaritySearchSnapshotScoped(query, snapshotIds, effectiveTopK(), effectiveSimilarityThreshold());
        raw = applyProjectAndDocumentFilter(raw);
        List<Document> staged =
                layout == DenseLayout.DOCUMENT_COMBINED ? formattingHelper.grouped(raw) : raw;
        StringBuilder sb = new StringBuilder();
        for (Document d : staged) {
            String block =
                    formattingHelper.filterDocumentContent(
                            d, query, null);
            if (block == null || block.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(block.trim());
        }
        if (layout == DenseLayout.CHUNK_SEPARATE_WITH_DB_METADATA) {
            String extra = loadDbMetadataAppendix(ctx, staged, snapshotIds);
            if (extra != null && !extra.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(extra.trim());
            }
        }
        return sb.toString();
    }

    private List<Document> similaritySearchSnapshotScoped(String query, List<UUID> snapshotIds, int topK, double sim) {
        SearchRequest req =
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(sim).build();
        List<Document> docs = vectorStore.similaritySearch(req);
        Set<String> allowed = snapshotIds.stream().map(UUID::toString).collect(Collectors.toSet());
        return docs.stream().filter(d -> snapshotMatches(d, allowed)).toList();
    }

    private static boolean snapshotMatches(Document d, Set<String> allowedSnapshotIdStrings) {
        if (d.getMetadata() == null) {
            return false;
        }
        Object sid = d.getMetadata().get("indexSnapshotId");
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
        List<Document> byProject =
                docs.stream().filter(d -> passesProjectMetadata(d, pid, ctx)).toList();
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
        if (d.getMetadata() == null) {
            return true;
        }
        Map<String, Object> meta = d.getMetadata();
        Object cs = meta.get("corpusScope");
        if ("CHAT_LOCAL".equalsIgnoreCase(String.valueOf(cs))) {
            if (ctx == null || ctx.conversationId() == null) {
                return false;
            }
            Object conv = meta.get("conversationId");
            return ctx.conversationId().equals(String.valueOf(conv));
        }
        Object p = meta.get("projectId");
        if (p == null) {
            return true;
        }
        return projectId.equals(String.valueOf(p));
    }

    private static boolean passesDocumentAllowlist(Document d, Set<String> allowed) {
        if (d.getMetadata() == null) {
            return false;
        }
        Object id = d.getMetadata().get("document_id");
        if (id == null) {
            id = d.getMetadata().get("documentId");
        }
        if (id == null) {
            id = d.getMetadata().get("projectDocumentId");
        }
        if (id == null) {
            return false;
        }
        return allowed.contains(String.valueOf(id));
    }

    private int effectiveTopK() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null && ctx.resolvedConfig().topK() > 0) {
            return ctx.resolvedConfig().topK();
        }
        return defaultTopK;
    }

    private double effectiveSimilarityThreshold() {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null && ctx.resolvedConfig().similarityThreshold() > 0) {
            return ctx.resolvedConfig().similarityThreshold();
        }
        return defaultSimilarityThreshold;
    }

    private String loadDbMetadataAppendix(
            ExecutionContext ctx, List<Document> staged, List<UUID> snapshotIds) {
        if (ctx.projectId() == null) {
            return "";
        }
        LinkedHashSet<UUID> docIds = new LinkedHashSet<>();
        for (Document d : staged) {
            if (d.getMetadata() == null) {
                continue;
            }
            Object id = d.getMetadata().get("document_id");
            if (id == null) {
                id = d.getMetadata().get("documentId");
            }
            if (id == null) {
                continue;
            }
            try {
                docIds.add(UUID.fromString(String.valueOf(id)));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        if (docIds.isEmpty()) {
            return "";
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("projectId", ctx.projectId());
        p.addValue("docIds", new ArrayList<>(docIds));
        p.addValue("snapshotIds", snapshotIds);
        p.addValue("artifactType", DocumentArtifactType.METADATA.name());
        List<String> payloads =
                namedJdbc.query(
                        """
                        SELECT da.payload_jsonb::text
                        FROM document_artifact da
                        JOIN project_documents kd ON kd.id = da.document_id
                        WHERE da.artifact_type = CAST(:artifactType AS VARCHAR)
                          AND kd.project_id = :projectId
                          AND kd.id IN (:docIds)
                          AND kd.current_index_snapshot_id IN (:snapshotIds)
                        """,
                        p,
                        (rs, rowNum) -> rs.getString(1));
        if (payloads.isEmpty()) {
            return "";
        }
        return payloads.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> "[metadata] " + s)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Exposes {@link BasicContextRetriever} grouping + prefix formatting without exposing the bean to workflows.
     */
    private static final class SnapshotRetrievalFormattingHelper extends BasicContextRetriever {

        private SnapshotRetrievalFormattingHelper(
                PgVectorStore vectorStore,
                ChatClient chatClient,
                int topK,
                double similarityThreshold,
                boolean knowledgeChatOverlayEnabled) {
            super(vectorStore, chatClient, topK, similarityThreshold, knowledgeChatOverlayEnabled);
        }

        @Override
        public String filterDocumentContent(Document doc, String query, JSONObject entities) {
            String content = doc.getText() != null ? doc.getText() : "";
            return buildContentWithOptionalMetadataPrefix(doc, content);
        }

        List<Document> grouped(List<Document> docs) {
            return groupAndCombineChunks(docs);
        }
    }
}
