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
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

class MetadataCountDocumentsToolTest {

    private static final String ACTA1_ID = "acta-1-doc";
    private static final String ACTA6_ID = "acta-6-doc";

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataCountDocumentsTool tool;
    private Map<String, Map<String, Object>> actaById;

    @BeforeEach
    void setUp() throws IOException {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        when(llmCache.getCachedResponse(anyString(), anyString())).thenReturn("NONE");
        tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);

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
        ToolExecutionContext ctx = ToolExecutionContext.of("¿Cuántos documentos hay?", QueryType.COUNT_DOCUMENTS, null);
        ToolResult result = tool.execute(ctx);
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataCountDocumentsTool", result.source());
    }

    @Test
    void execute_withNer_returnsToolResult() {
        JSONObject ner = new JSONObject();
        ToolExecutionContext ctx = ToolExecutionContext.of("count by date", QueryType.COUNT_DOCUMENTS, ner);
        ToolResult result = tool.execute(ctx);
        assertNotNull(result);
        assertNotNull(result.result());
    }

    @Test
    void fdCd01_ascensorCount_returnsExactSpanishAnswer() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result())
                .isEqualTo(
                        "El ascensor se menciona en dos actas: ACTA 1.pdf (24/02/2025) y ACTA 6.pdf (25/08/2026).");
    }

    @Test
    void fdCd01_ascensorTopic_matchesActa6AfterStructuredReindex() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result()).contains("ACTA 6.pdf", "25/08/2026");
    }

    @Test
    void fdCd01_excludesSupersededSnapshotDuplicate() {
        String activeSnapshot = "4ec7f3a7-e0ba-4729-ad5e-a17102058d84";
        String supersededSnapshot = "822157f6-6ef7-4430-aa55-0c9435d9ab20";
        List<Document> docs = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : actaById.entrySet()) {
            docs.add(toSnapshotDoc(entry.getKey(), entry.getValue(), activeSnapshot, "active body"));
        }
        Map<String, Object> staleActa1 = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        docs.add(toSnapshotDoc("stale-acta-1", staleActa1, supersededSnapshot, "stale ascensor duplicate"));
        Map<String, Object> legacyMeta = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        legacyMeta.put("filename", "acta-24-02-2025.txt");
        legacyMeta.put("sourceTitle", "acta-24-02-2025.txt");
        docs.add(toDoc("legacy-txt", legacyMeta, "legacy txt ascensor"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer)
                .isEqualTo(
                        "El ascensor se menciona en dos actas: ACTA 1.pdf (24/02/2025) y ACTA 6.pdf (25/08/2026).");
        assertThat(answer).doesNotContain("acta-24-02-2025.txt", "tres actas", "3 actas");
    }

    @Test
    void fdCd01_ascensorCount_returnsActa1AndActa6WithSlashDates() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf", "24/02/2025", "25/08/2026");
        assertThat(answer.toLowerCase(Locale.ROOT)).containsAnyOf("dos actas", "2 actas", "en 2 reuniones", "en 2 actas");
    }

    @Test
    void fdCd01_ascensorCount_scansFullCorpusWhenChunkFilterWouldMissActa6() {
        List<Document> rankedOnlyActa1 =
                allDocs().stream()
                        .filter(d -> ACTA1_ID.equals(d.getMetadata().get("document_id")))
                        .toList();
        when(retriever.retrieve(anyString())).thenAnswer(
                invocation -> {
                    String q = invocation.getArgument(0);
                    if (q != null && q.contains("junta propietarios")) {
                        return allDocs();
                    }
                    return rankedOnlyActa1;
                });
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class)))
                .thenReturn(rankedOnlyActa1);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf", "24/02/2025", "25/08/2026");
        assertThat(answer.toLowerCase(Locale.ROOT)).containsAnyOf("dos actas", "2 actas");
    }

    @Test
    void englishElevatorCount_returnsEnglishAnswerWithActaReferences() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "How many meetings mention the elevator?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf", "meetings");
        assertThat(answer.toLowerCase(Locale.ROOT)).contains("elevator");
        assertThat(answer).doesNotContain("actas");
    }

    @Test
    void fdCd01_dedupeActa1PdfAndTxtAliasCountsOnce() {
        List<Document> docs = new ArrayList<>(allDocs());
        Map<String, Object> acta1Meta = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        acta1Meta.put("filename", "acta-24-02-2025.txt");
        acta1Meta.put("sourceTitle", "acta-24-02-2025.txt");
        docs.add(toDoc("acta-24-02-2025-alias", acta1Meta, "duplicate alias mentions ascensor"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf");
        assertThat(answer.toLowerCase(Locale.ROOT)).containsAnyOf("dos actas", "2 actas");
        assertThat(answer).doesNotContain("acta-24-02-2025.txt");
    }

    @Test
    void fdCd02_year2025_dedupesTxtAliasFromPdfActa1() {
        List<Document> docs = new ArrayList<>(allDocs());
        Map<String, Object> acta1Meta = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        acta1Meta.put("filename", "acta-24-02-2025.txt");
        acta1Meta.put("sourceTitle", "acta-24-02-2025.txt");
        docs.add(toDoc("acta-24-02-2025-alias", acta1Meta, "duplicate alias body"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones hubo en 2025?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("Hubo 3 reuniones en 2025", "24/02/2025", "25/02/2025", "25/08/2025");
        assertThat(answer).doesNotContain("Hubo 4 reuniones", "Hubo 7 reuniones", "acta-24-02-2025.txt");
    }

    @Test
    void fdCd02_year2025_dedupesSevenHybridRowsToThreeMeetings() {
        List<Document> docs = new ArrayList<>();
        docs.add(toDoc(ACTA1_ID, actaById.get(ACTA1_ID), "acta 1 body"));
        docs.add(toDoc("acta-2-doc", actaById.get("acta-2-doc"), "acta 2 body"));
        docs.add(toDoc("acta-3-doc", actaById.get("acta-3-doc"), "acta 3 body"));
        docs.add(toDoc("snap-2025-02-24-a", stripDateIso(actaById.get(ACTA1_ID)), "chunk a"));
        docs.add(toDoc("snap-2025-02-24-b", stripDateIso(actaById.get(ACTA1_ID)), "chunk b"));
        docs.add(toDoc("snap-2025-02-25", stripDateIso(actaById.get("acta-2-doc")), "chunk c"));
        docs.add(toDoc("acta-24-02-2025.txt", aliasMetaWithoutIso(ACTA1_ID), "txt alias"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones hubo en 2025?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("Hubo 3 reuniones en 2025");
        assertThat(answer).contains("24/02/2025", "25/02/2025", "25/08/2025");
        assertThat(answer).doesNotContain("Hubo 7", "Hubo 4", "Hubo 5", "Hubo 6");
    }

    @Test
    void fdCd02_year2025_exactStageAQuery() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones hubo en 2025?", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result())
                .contains("3", "2025", "24/02/2025", "25/02/2025", "25/08/2025");
    }

    @Test
    void fdCd03_year2028_negativeAnswerExplicitlyMentions2028() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Número de actas registradas en el año 2028.", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result())
                .isEqualTo("No existen actas correspondientes al año 2028 en el corpus.");
        assertThat(result.result()).doesNotContain("esa fecha", "Se encontraron 2028 actas");
    }

    @Test
    void fdCd03_year2028_negativeAnswer_doesNotUseGenericDateTemplate() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Número de actas registradas en el año 2028.", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result())
                .doesNotContain("esa fecha")
                .doesNotContain("ninguna acta registrada en esa fecha");
    }

    @Test
    void fdCd03_year2028_negativeAnswer_doesNotInventActas() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Número de actas registradas en el año 2028.", QueryType.COUNT_DOCUMENTS, null));

        assertThat(result.result()).doesNotContain("ACTA", "acta del", "Se encontraron");
    }

    @Test
    void fdCd04_solarRadiationNegative_doesNotEchoDisallowedTopicPhrase() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Se habló de la radiación solar en alguna reunión?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).doesNotContain("radiación solar");
        assertThat(answer).containsAnyOf("ninguna acta", "no se menciona", "no se encontr", "no hay");
    }

    @Test
    void fdCd01_hybridRowsAndLegacyTxt_excludesTxtAndCountsTwo() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(hybridChunksForActa(ACTA1_ID, actaById.get(ACTA1_ID), 3));
        docs.addAll(hybridChunksForActa(ACTA6_ID, actaById.get(ACTA6_ID), 3));
        Map<String, Object> legacyMeta = new LinkedHashMap<>(actaById.get(ACTA1_ID));
        legacyMeta.put("filename", "acta-24-02-2025.txt");
        legacyMeta.put("sourceTitle", "acta-24-02-2025.txt");
        docs.add(toDoc("legacy-txt", legacyMeta, "legacy mentions ascensor"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf");
        assertThat(answer.toLowerCase(Locale.ROOT)).containsAnyOf("dos actas", "2 actas");
        assertThat(answer).doesNotContain("acta-24-02-2025.txt", "tres actas", "3 actas");
    }

    @Test
    void fdCd01_ascensor_matchesWhenTopicOnlyInHybridSectionBody() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(
                sparseHybridChunksWithSectionBody(
                        ACTA1_ID, actaById.get(ACTA1_ID), "Se informa sobre la necesidad de reparar el ascensor"));
        docs.addAll(
                sparseHybridChunksWithSectionBody(
                        ACTA6_ID,
                        actaById.get(ACTA6_ID),
                        "Se presentan propuestas para modernizar el ascensor del edificio."));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer)
                .isEqualTo(
                        "El ascensor se menciona en dos actas: ACTA 1.pdf (24/02/2025) y ACTA 6.pdf (25/08/2026).");
        assertThat(answer).doesNotContain(".txt");
    }

    @Test
    void fdCd01_scopedCorpus_containsActa1AndActa6ForAscensor() throws IOException {
        List<Document> docs = new ArrayList<>();
        docs.addAll(sparseHybridChunksWithSectionBody(ACTA1_ID, actaById.get(ACTA1_ID), "reparar el ascensor"));
        docs.addAll(
                sparseHybridChunksWithSectionBody(
                        ACTA6_ID, actaById.get(ACTA6_ID), "mejora del ascensor en el edificio"));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS, null));

        String answer = result.result();
        assertThat(answer).contains("ACTA 1.pdf", "ACTA 6.pdf", "24/02/2025", "25/08/2026");
        assertThat(answer.toLowerCase(Locale.ROOT)).containsAnyOf("dos actas", "2 actas");
    }

    @Test
    void beatrizSuarez_exactlyFiveActas() {
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿En cuántas actas aparece Beatriz Suárez Aguilar?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).containsAnyOf("5", "cinco");
        assertThat(answer).contains("beatriz");
    }

    private List<Document> sparseHybridChunksWithSectionBody(
            String projectDocId, Map<String, Object> meta, String sectionBody) {
        List<Document> chunks = new ArrayList<>();
        Map<String, Object> headerMeta = sparseChunkMeta(projectDocId, meta, 0);
        chunks.add(new Document("encabezado de acta", headerMeta));
        Map<String, Object> sectionMeta = sparseChunkMeta(projectDocId, meta, 1);
        chunks.add(new Document(sectionBody, sectionMeta));
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
        return chunkMeta;
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
    void detectStartTimeCountQuery_matchesSpanishStartTimeCount() throws Exception {
        var method =
                MetadataCountDocumentsTool.class.getDeclaredMethod(
                        "detectStartTimeCountQuery", String.class, JSONObject.class);
        method.setAccessible(true);
        Object detected =
                method.invoke(
                        tool,
                        "¿Cuántas reuniones comenzaron a las 19:00 horas?",
                        new JSONObject());
        assertNotNull(detected);
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

    private Document toSnapshotDoc(String docId, Map<String, Object> meta, String snapshotId, String text) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("document_id", docId);
        chunkMeta.put("indexSnapshotId", snapshotId);
        StructuredMinuteMetadataSupport.flattenMetadata(meta).forEach(chunkMeta::put);
        return new Document(text, chunkMeta);
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

    private static Map<String, Object> stripDateIso(Map<String, Object> meta) {
        Map<String, Object> copy = new LinkedHashMap<>(meta);
        copy.remove("date_iso");
        copy.remove("actaDate");
        return copy;
    }

    private Map<String, Object> aliasMetaWithoutIso(String canonicalDocId) {
        Map<String, Object> copy = new LinkedHashMap<>(actaById.get(canonicalDocId));
        copy.remove("date_iso");
        copy.remove("actaDate");
        copy.put("filename", "acta-24-02-2025.txt");
        copy.put("sourceTitle", "acta-24-02-2025.txt");
        return copy;
    }
}
