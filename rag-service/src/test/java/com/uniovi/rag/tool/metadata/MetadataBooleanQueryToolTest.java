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
import java.util.ArrayList;
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

class MetadataBooleanQueryToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataBooleanQueryTool tool;
    private Map<String, Map<String, Object>> actaById;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        tool = new MetadataBooleanQueryTool(chatClient, retriever, extractor, llmCache);

        MetadataMinuteDocumentService metadataService =
                new MetadataMinuteDocumentService(
                        mock(PgVectorStore.class), mock(ChatClient.class), mock(JdbcTemplate.class), 400);
        actaById = new LinkedHashMap<>();
        loadActa(metadataService, "ACTA 1.pdf", "acta-1.txt", "acta-1-doc");
        loadActa(metadataService, "ACTA 2.pdf", "acta-2.txt", "acta-2-doc");
        loadActa(metadataService, "ACTA 3.pdf", "acta-3.txt", "acta-3-doc");
        loadActa(metadataService, "ACTA 5.pdf", "acta-5.txt", "acta-5-doc");
        loadActa(metadataService, "ACTA 6.pdf", "acta-6.txt", "acta-6-doc");

        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("¿se aprobó?", QueryType.BOOLEAN_QUERY, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataBooleanQueryTool", result.source());
    }

    @Test
    void fdBq03_fewerThan10Participants_answerInSpanishWithExpectedWording() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Hay actas con menos de 10 participantes?", QueryType.BOOLEAN_QUERY, null));

        assertThat(result.result())
                .isEqualTo(
                        "No; no hay actas con menos de 10 participantes. Todas las actas registran al menos 17 participantes (mínimo 17, máximo 20).");
        assertThat(result.result()).contains("10");
    }

    @Test
    void fdBq02_limpieza2026_returnsDeterministicNegativeWithYear() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result())
                .isEqualTo("No, la limpieza no se menciona en ninguna acta de 2026.");
        assertThat(result.result()).contains("2026");
        assertThat(result.result().toLowerCase()).startsWith("no");
        assertThat(result.result()).doesNotContain("Explanation");
    }

    @Test
    void fdBq02_limpieza2026_deterministicEvenWhenKeywordLlmWouldSayYes() {
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "YES");
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result())
                .isEqualTo("No, la limpieza no se menciona en ninguna acta de 2026.");
        assertThat(result.result()).contains("limpieza", "2026");
        assertThat(result.result()).doesNotContain("Explanation");
    }

    @Test
    void fdBq01_jorgeOnActaDate_preservesAffirmativeSpanishAnswer() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Confirma si Jorge Moreno Navarro aparece en el acta del 25 de agosto de 2026.",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result().toLowerCase()).contains("jorge");
        assertThat(result.result().toLowerCase()).startsWith("sí");
        assertThat(result.result()).doesNotContain("Explanation");
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

    private List<Document> allDocs() {
        List<Document> docs = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : actaById.entrySet()) {
            docs.add(toDoc(entry.getKey(), entry.getValue(), "full acta text"));
        }
        return docs;
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
