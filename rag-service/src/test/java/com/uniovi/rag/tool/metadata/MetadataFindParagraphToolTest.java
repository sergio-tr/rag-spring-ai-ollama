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

class MetadataFindParagraphToolTest {

    private static final String ACTA1_ID = "acta-1-doc";
    private static final String ACTA2_ID = "acta-2-doc";

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataFindParagraphTool tool;
    private Map<String, Map<String, Object>> actaById;
    private Map<String, String> fixtureByDocId;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        tool = new MetadataFindParagraphTool(chatClient, retriever, extractor, llmCache);

        MetadataMinuteDocumentService metadataService =
                new MetadataMinuteDocumentService(
                        mock(PgVectorStore.class), mock(ChatClient.class), mock(JdbcTemplate.class), 400);
        actaById = new LinkedHashMap<>();
        fixtureByDocId = new LinkedHashMap<>();
        loadActa(metadataService, "ACTA 1.pdf", "acta-1.txt", ACTA1_ID);
        loadActa(metadataService, "ACTA 2.pdf", "acta-2.txt", ACTA2_ID);
        loadActa(metadataService, "ACTA 3.pdf", "acta-3.txt", "acta-3-doc");
        loadActa(metadataService, "ACTA 5.pdf", "acta-5.txt", "acta-5-doc");
        loadActa(metadataService, "ACTA 6.pdf", "acta-6.txt", "acta-6-doc");

        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("find paragraph", QueryType.FIND_PARAGRAPH, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataFindParagraphTool", result.source());
    }

    @Test
    void fdFp01_returnsActa2DateTopicAndContractEvidence() throws IOException {
        stubRetriever(allDocsWithFixtureBodies());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué se dijo en relación a la limpieza de las zonas comunes?",
                                QueryType.FIND_PARAGRAPH,
                                null));

        String answer = result.result();
        assertThat(answer)
                .isEqualTo(
                        "En el acta del 25/02/2025 se plantea la necesidad de mejorar la limpieza en las zonas comunes. Se aprueba la contratación de un nuevo servicio de limpieza con mayor frecuencia.");
        assertThat(answer).contains("25/02/2025", "limpieza", "contratación");
        assertThat(answer).doesNotContain("ACTA 1", "estacionamiento", "señalización");
    }

    @Test
    void fdFp01_withOnlyActa1_doesNotInventActa2LimpiezaEvidence() throws IOException {
        stubRetriever(docsWithBodiesFor(ACTA1_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué se dijo en relación a la limpieza de las zonas comunes?",
                                QueryType.FIND_PARAGRAPH,
                                null));

        assertThat(result.result().toLowerCase())
                .doesNotContain("25/02/2025")
                .doesNotContain("contratación de un nuevo servicio de limpieza");
    }

    @Test
    void fdFp02_gasLeakAbsent_returnsDeterministicNegativeWithoutHedging() throws IOException {
        stubRetriever(allDocsWithFixtureBodies());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué se comentó respecto a la fuga de gas?",
                                QueryType.FIND_PARAGRAPH,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "No se encuentra ninguna mención a una fuga de gas en las actas disponibles.");
        assertThat(result.result()).doesNotContain("cámaras", "pendiente de estudio");
    }

    private void stubRetriever(List<Document> docs) {
        when(retriever.retrieve(anyString())).thenReturn(docs);
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(docs);
    }

    private List<Document> allDocsWithFixtureBodies() throws IOException {
        List<Document> docs = new ArrayList<>();
        for (String docId : actaById.keySet()) {
            docs.addAll(docsWithBodiesFor(docId));
        }
        return docs;
    }

    private List<Document> docsWithBodiesFor(String docId) throws IOException {
        String body = readFixtureBody(fixtureByDocId.get(docId));
        return List.of(toDoc(docId, actaById.get(docId), body));
    }

    private void loadActa(MetadataMinuteDocumentService metadataService, String pdfName, String fixture, String docId)
            throws IOException {
        String text = readFixtureBody(fixture);
        metadataService
                .tryExtractDeterministicMetadataForIndexing(text, pdfName, docId)
                .ifPresent(meta -> actaById.put(docId, meta));
        fixtureByDocId.put(docId, fixture);
    }

    private static String readFixtureBody(String fixture) throws IOException {
        return Files.readString(Path.of("src/test/resources/acta-fixtures", fixture), StandardCharsets.UTF_8);
    }

    private static Document toDoc(String id, Map<String, Object> meta, String body) {
        Map<String, Object> metadata = new LinkedHashMap<>(meta);
        metadata.put("projectDocumentId", id);
        metadata.put("document_id", id);
        return new Document(body, metadata);
    }
}
