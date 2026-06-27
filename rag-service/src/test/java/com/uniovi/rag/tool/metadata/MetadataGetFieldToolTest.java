package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.document.MetadataMinuteDocumentService;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class MetadataGetFieldToolTest {

    private static final String ACTA6_ID = "acta-6-doc";

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataGetFieldTool tool;
    private Map<String, Map<String, Object>> actaById;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);

        MetadataMinuteDocumentService metadataService =
                new MetadataMinuteDocumentService(
                        mock(PgVectorStore.class), mock(ChatClient.class), mock(JdbcTemplate.class), 400);
        actaById = new LinkedHashMap<>();
        loadActa(metadataService, "ACTA 6.pdf", "acta-6.txt", ACTA6_ID);

        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("fecha del acta", QueryType.GET_FIELD, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataGetFieldTool", result.source());
    }

    @Test
    void jorgeRoleOnActa6_mentionsPresidentManuelOrtegaMedina() {
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué papel tuvo Jorge Moreno Navarro en la reunión del 25/08/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result())
                .contains("Jorge Moreno Navarro", "Manuel Ortega Medina", "asistente", "presidencia")
                .doesNotContainPattern("(?i)jorge.*presidente");
    }

    private void loadActa(
            MetadataMinuteDocumentService metadataService, String filename, String fixture, String docId)
            throws IOException {
        String content = Files.readString(fixturePath(fixture), StandardCharsets.UTF_8);
        metadataService
                .tryExtractDeterministicMetadataForIndexing(content, filename, docId)
                .ifPresent(meta -> actaById.put(docId, meta));
    }

    private Path fixturePath(String fixture) {
        return Path.of("src/test/resources/acta-fixtures", fixture);
    }

    private List<Document> docsForActa(String docId) {
        return List.of(toDoc(docId, actaById.get(docId), "acta body"));
    }

    private Document toDoc(String docId, Map<String, Object> meta, String text) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("document_id", docId);
        StructuredMinuteMetadataSupport.flattenMetadata(meta).forEach(chunkMeta::put);
        return new Document(text, chunkMeta);
    }

    private void stubRetriever(List<Document> docs) {
        when(retriever.retrieve(anyString())).thenReturn(docs);
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(docs);
    }
}
