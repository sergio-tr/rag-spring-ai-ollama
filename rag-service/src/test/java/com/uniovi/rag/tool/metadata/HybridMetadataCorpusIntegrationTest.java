package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/** HYBRID chunk metadata dedupe before deterministic metadata tool corpus scan. */
class HybridMetadataCorpusIntegrationTest {

    private MetadataCountDocumentsTool tool;

    @BeforeEach
    void setUp() {
        ChatClient chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService cache = mock(MetadataLlmResponseCacheService.class);
        when(cache.getCachedResponse(anyString())).thenReturn("");
        when(cache.getCachedResponse(anyString(), anyString())).thenReturn("NONE");
        tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, cache);
    }

    @Test
    void hybridSnapshotDedupe_retainsActiveCanonicalMeetingPerFilename() {
        String activeSnapshot = "4ec7f3a7-e0ba-4729-ad5e-a17102058d84";
        Document active = chunk("acta-1", "ACTA 1.pdf", "2025-02-24", activeSnapshot, "a");
        Document stale = chunk("acta-1-old", "ACTA 1.pdf", "2025-02-24", "822157f6-6ef7-4430-aa55-0c9435d9ab20", "b");
        Document second = chunk("acta-2", "ACTA 2.pdf", "2025-02-25", activeSnapshot, "c");

        List<Document> scoped = tool.filterScopedCorpusDocuments(List.of(active, stale, second));

        assertThat(scoped).containsExactly(active, second);
    }

    @Test
    void hybridDedupedCorpus_metadataToolExecution_countsCanonicalMeetingsOnly() {
        String activeSnapshot = "4ec7f3a7-e0ba-4729-ad5e-a17102058d84";
        Document active = chunk("acta-1", "ACTA 1.pdf", "2025-02-24", activeSnapshot, "ascensor");
        Document stale = chunk("acta-1-old", "ACTA 1.pdf", "2025-02-24", "822157f6-6ef7-4430-aa55-0c9435d9ab20", "ascensor stale");
        Document second = chunk("acta-2", "ACTA 2.pdf", "2025-02-25", activeSnapshot, "ascensor");
        List<Document> canonical = List.of(active, second);

        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(anyString())).thenReturn(canonical);
        when(retriever.retrieveWithMetadataFilters(anyString(), any())).thenReturn(canonical);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService cache = mock(MetadataLlmResponseCacheService.class);
        when(cache.getCachedResponse(anyString())).thenReturn("");
        when(cache.getCachedResponse(anyString(), anyString())).thenReturn("NONE");
        MetadataCountDocumentsTool countTool =
                new MetadataCountDocumentsTool(
                        ChatClientTestSupport.mockForUserPromptChain(), retriever, extractor, cache);

        ToolResult result =
                countTool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result()).contains("2");
        assertThat(countTool.filterScopedCorpusDocuments(List.of(active, stale, second)))
                .containsExactlyElementsOf(canonical);
    }

    private static Document chunk(
            String docId, String filename, String dateIso, String snapshotId, String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("document_id", docId);
        meta.put("filename", filename);
        meta.put("date_iso", dateIso);
        meta.put("indexSnapshotId", snapshotId);
        meta.put("projectDocumentId", "pdoc-" + docId);
        return new Document(text, meta);
    }
}
