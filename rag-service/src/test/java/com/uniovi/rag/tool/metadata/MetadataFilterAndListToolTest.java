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

class MetadataFilterAndListToolTest {

    private static final String ACTA1_ID = "acta-1-doc";
    private static final String ACTA6_ID = "acta-6-doc";

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataFilterAndListTool tool;
    private Map<String, Map<String, Object>> actaById;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        tool = new MetadataFilterAndListTool(chatClient, retriever, extractor, llmCache);

        MetadataMinuteDocumentService metadataService =
                new MetadataMinuteDocumentService(
                        mock(PgVectorStore.class), mock(ChatClient.class), mock(JdbcTemplate.class), 400);
        actaById = new LinkedHashMap<>();
        loadActa(metadataService, "ACTA 1.pdf", "acta-1.txt", ACTA1_ID);
        loadActa(metadataService, "ACTA 2.pdf", "acta-2.txt", "acta-2-doc");
        loadActa(metadataService, "ACTA 3.pdf", "acta-3.txt", "acta-3-doc");
        loadActa(metadataService, "ACTA 5.pdf", "acta-5.txt", "acta-5-doc");
        loadActa(metadataService, "ACTA 6.pdf", "acta-6.txt", ACTA6_ID);

        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("listar actas", QueryType.FILTER_AND_LIST, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataFilterAndListTool", result.source());
    }

    @Test
    void fdFl02_ascensorAndPresident_returnsExactEvaluatorAnswer() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "Solo el acta del 24/02/2025 (ACTA 1.pdf) menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.");
    }

    @Test
    void fdFl02_sparseHybridSectionBody_selectsActa1NotActa6() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(
                sparseHybridChunksWithSectionBody(
                        ACTA1_ID,
                        actaById.get(ACTA1_ID),
                        "Se informa sobre la necesidad de reparar el ascensor y renovar la pintura del portal."));
        docs.addAll(
                sparseHybridChunksWithSectionBody(
                        ACTA6_ID,
                        actaById.get(ACTA6_ID),
                        "Se presentan diferentes propuestas para modernizar el ascensor del edificio."));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "Solo el acta del 24/02/2025 (ACTA 1.pdf) menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.");
        assertThat(result.result()).doesNotContain("ACTA 6", "Manuel Ortega");
    }

    @Test
    void fdFl02_compoundFilter_neverUsesLlmFallbackWhenStubReturnsGarbage() {
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "LLM_GARBAGE_SHOULD_NOT_APPEAR");
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "Solo el acta del 24/02/2025 (ACTA 1.pdf) menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.");
        assertThat(result.result()).doesNotContain("LLM_GARBAGE");
    }

    @Test
    void fdFl02_ascensorAndPresident_includesSlashDate24Feb2025() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result()).contains("24/02/2025", "ACTA 1", "ascensor", "Juan Pérez Gutiérrez");
    }

    @Test
    void fdFl03_augustVideovigilanciaOver18_returnsExactEvaluatorAnswer() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.");
    }

    @Test
    void fdFl03_augustVideovigilanciaOver18_returnsActa6WithMandatoryFields() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                                QueryType.FILTER_AND_LIST,
                                null));

        String answer = result.result();
        assertThat(answer)
                .contains("25/08/2026", "ACTA 6", "videovigilancia", "19")
                .doesNotContain("¿A qué acta")
                .doesNotContain("25/08/2025");
    }

    @Test
    void fdFl01_startTime1900_returnsCanonicalSlashDatesNotChunks() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué actas tienen hora de inicio a las 19:00?",
                                QueryType.FILTER_AND_LIST,
                                null));

        String answer = result.result();
        assertThat(answer).contains("Hay 3 actas", "24/02/2025", "25/02/2025", "25/02/2026");
        assertThat(answer).doesNotContain("Hay 9 actas", "Hay 6 actas");
    }

    @Test
    void fdFl01_startTime1900_hybridRowsDedupeToThreeActas() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(hybridChunksForActa(ACTA1_ID, actaById.get(ACTA1_ID), 3));
        docs.addAll(hybridChunksForActa("acta-2-doc", actaById.get("acta-2-doc"), 3));
        docs.addAll(hybridChunksForActa("acta-5-doc", actaById.get("acta-5-doc"), 3));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué actas tienen hora de inicio a las 19:00?",
                                QueryType.FILTER_AND_LIST,
                                null));

        String answer = result.result();
        assertThat(answer).contains("Hay 3 actas", "19:00");
        assertThat(answer).contains("24/02/2025", "25/02/2025", "25/02/2026");
        assertThat(answer).contains("ACTA 1", "ACTA 2", "ACTA 5");
        assertThat(answer).doesNotContain("Hay 9 actas", "Hay 6 actas");
    }

    @Test
    void fdFl01_startTime1900_excludesLegacyTxtAlias() {
        List<Document> docs = new ArrayList<>(allDocs());
        Map<String, Object> legacyMeta = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        legacyMeta.put("filename", "acta-24-02-2025.txt");
        legacyMeta.put("sourceTitle", "acta-24-02-2025.txt");
        docs.add(toDoc("legacy-txt", legacyMeta, "legacy txt body"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué actas tienen hora de inicio a las 19:00?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result()).contains("Hay 3 actas");
        assertThat(result.result()).doesNotContain("acta-24-02-2025.txt", "Hay 4 actas");
    }

    private List<Document> hybridChunksForActa(String projectDocId, Map<String, Object> meta, int chunkCount) {
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(toHybridChunk(projectDocId + "-chunk-" + i, projectDocId, meta, "chunk " + i, i));
        }
        return chunks;
    }

    private Document toHybridChunk(
            String chunkId, String projectDocId, Map<String, Object> meta, String text, int chunkIndex) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("document_id", chunkId);
        chunkMeta.put("projectDocumentId", projectDocId);
        chunkMeta.put("chunkIndex", chunkIndex);
        chunkMeta.put("indexSnapshotId", "4ec7f3a7-e0ba-4729-ad5e-a17102058d84");
        StructuredMinuteMetadataSupport.flattenMetadata(meta).forEach(chunkMeta::put);
        return new Document(text, chunkMeta);
    }

    @Test
    void fdFl03_excludesAugustMeetingWithExactly18Attendees() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result()).contains("ACTA 6", "19");
        assertThat(result.result()).doesNotContain("ACTA 3");
    }

    @Test
    void fdFl03_sparseHybridSectionBodyOnly_selectsActa6NotActa3() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(sparseHybridChunksWithSectionBody(ACTA6_ID, actaById.get(ACTA6_ID), "nuevo sistema de videovigilancia en entradas y garajes"));
        docs.addAll(sparseHybridChunksWithSectionBody("acta-3-doc", actaById.get("acta-3-doc"), "encabezado sin videovigilancia"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.");
        assertThat(result.result()).doesNotContain("ACTA 3", "25/08/2025", "18 asistentes");
    }

    @Test
    void fdFl03_compoundFilter_neverUsesLlmFallbackWhenStubReturnsGarbage() {
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "LLM_GARBAGE_SHOULD_NOT_APPEAR");
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result())
                .isEqualTo(
                        "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.");
        assertThat(result.result()).doesNotContain("LLM_GARBAGE", "august", "celebrated");
    }

    private List<Document> sparseHybridChunksWithSectionBody(
            String projectDocId, Map<String, Object> meta, String sectionBody) {
        List<Document> chunks = new ArrayList<>();
        chunks.add(new Document("encabezado", sparseChunkMeta(projectDocId, meta, 0)));
        chunks.add(new Document(sectionBody, sparseChunkMeta(projectDocId, meta, 1)));
        return chunks;
    }

    private Map<String, Object> sparseChunkMeta(String projectDocId, Map<String, Object> meta, int chunkIndex) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("document_id", projectDocId + "-sparse-" + chunkIndex);
        chunkMeta.put("projectDocumentId", projectDocId);
        chunkMeta.put("chunkIndex", chunkIndex);
        chunkMeta.put("indexSnapshotId", "4ec7f3a7-e0ba-4729-ad5e-a17102058d84");
        chunkMeta.put("filename", meta.get("filename"));
        chunkMeta.put("date_iso", meta.get("date_iso"));
        chunkMeta.put("date", meta.get("date"));
        Object president = meta.get("president");
        if (president != null) {
            chunkMeta.put("president", president);
        }
        Object attendeesCount = meta.get("attendeesCount");
        if (attendeesCount != null) {
            chunkMeta.put("attendeesCount", attendeesCount);
        }
        Object numberOfAttendees = meta.get("numberOfAttendees");
        if (numberOfAttendees != null) {
            chunkMeta.put("numberOfAttendees", numberOfAttendees);
        }
        return chunkMeta;
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
