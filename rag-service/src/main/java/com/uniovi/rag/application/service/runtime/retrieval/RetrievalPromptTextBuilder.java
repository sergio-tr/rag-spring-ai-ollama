package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.application.service.runtime.retrieval.BasicContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.RetrievalChunkGroupingHelper;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds deterministic prompt context text from curated candidates (metadata prefix + optional chunk grouping).
 */
@Service
public class RetrievalPromptTextBuilder {

    private final RetrievalChunkGroupingHelper groupingHelper;
    private final MetadataPrefixFormatter formatter;

    public RetrievalPromptTextBuilder(
            PgVectorStore vectorStore,
            ChatClient chatClient,
            @Value("${spring.ai.ollama.top-k:10}") int defaultTopK,
            @Value("${spring.ai.ollama.similarity-threshold:0.7}") double defaultSimilarityThreshold,
            @Value("${knowledge.v2.chat-overlay.enabled:false}") boolean knowledgeChatOverlayEnabled) {
        this.groupingHelper = new RetrievalChunkGroupingHelper(vectorStore, chatClient, defaultTopK, defaultSimilarityThreshold);
        this.formatter = new MetadataPrefixFormatter(vectorStore, chatClient, defaultTopK, defaultSimilarityThreshold, knowledgeChatOverlayEnabled);
    }

    public String build(List<RetrievalCandidate> candidates, String query, RetrievalLayout layout) {
        if (candidates.isEmpty()) {
            return "";
        }
        List<Document> docs = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            Map<String, Object> meta = new HashMap<>(c.metadata());
            docs.add(new Document(c.content(), meta));
        }
        if (layout == RetrievalLayout.DOCUMENT_COMBINED) {
            docs = groupingHelper.groupDocuments(docs);
        }
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            String block = formatter.filterDocumentContent(d, query, null);
            if (block == null || block.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(block.trim());
        }
        return sb.toString();
    }

    private static final class MetadataPrefixFormatter extends BasicContextRetriever {

        private MetadataPrefixFormatter(
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
    }
}
