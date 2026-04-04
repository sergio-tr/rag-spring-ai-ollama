package com.uniovi.rag.service.chat;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.service.config.ConfigResolver;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds explainability "sources" for chat SSE by running the same scoped {@link ContextRetriever}
 * the RAG pipeline uses (after the main query clears {@link RagExecutionContextHolder}).
 */
@Service
public class ChatRetrievalSourceContributor {

    private static final Logger log = LoggerFactory.getLogger(ChatRetrievalSourceContributor.class);

    private static final int MAX_SOURCES = 8;
    private static final int SNIPPET_MAX = 240;

    private final ConfigResolver configResolver;
    private final ContextRetriever contextRetriever;

    public ChatRetrievalSourceContributor(ConfigResolver configResolver, ContextRetriever contextRetriever) {
        this.configResolver = configResolver;
        this.contextRetriever = contextRetriever;
    }

    /**
     * @param docFilter conversation document allowlist (same semantics as {@link RagExecutionContext})
     */
    public List<Map<String, Object>> buildSources(
            UUID userId,
            UUID projectId,
            UUID conversationId,
            List<String> docFilter,
            String userContent) {
        if (userContent == null || userContent.isBlank() || projectId == null) {
            return List.of();
        }
        try {
            RagConfig resolved = configResolver.resolve(userId, projectId, null);
            List<String> filter = (docFilter == null || docFilter.isEmpty())
                    ? List.of(RagExecutionContext.ALL_DOCUMENTS)
                    : docFilter;
            RagExecutionContext ctx = new RagExecutionContext(
                    conversationId != null ? conversationId.toString() : null,
                    userId != null ? userId.toString() : null,
                    projectId.toString(),
                    resolved,
                    filter,
                    null);
            RagExecutionContextHolder.set(ctx);
            try {
                List<Document> docs = contextRetriever.retrieve(userContent.trim());
                return mapDocuments(docs);
            } finally {
                RagExecutionContextHolder.clear();
            }
        } catch (Exception e) {
            log.warn("Chat sources retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<Map<String, Object>> mapDocuments(List<Document> docs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (docs == null) {
            return out;
        }
        int n = Math.min(docs.size(), MAX_SOURCES);
        for (int i = 0; i < n; i++) {
            Document d = docs.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Object> meta = d.getMetadata();
            if (meta != null) {
                copyMeta(m, "filename", meta.get("filename"));
                copyMeta(m, "document_id", meta.get("document_id"));
                copyMeta(m, "projectDocumentId", meta.get("projectDocumentId"));
                copyMeta(m, "chunk_index", meta.get("chunk_index"));
                copyMeta(m, "distance", meta.get("distance"));
            }
            String text = d.getText();
            if (text != null && !text.isBlank()) {
                String t = text.trim();
                if (t.length() > SNIPPET_MAX) {
                    m.put("snippet", t.substring(0, SNIPPET_MAX) + "…");
                } else {
                    m.put("snippet", t);
                }
            }
            if (!m.isEmpty()) {
                out.add(m);
            }
        }
        return out;
    }

    private static void copyMeta(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (!s.isEmpty()) {
            target.put(key, value);
        }
    }
}
