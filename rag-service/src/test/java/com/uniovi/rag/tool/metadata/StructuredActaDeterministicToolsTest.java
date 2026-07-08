package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.HashMap;
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

/**
 * Phase 6: structured-metadata-first deterministic tool behavior on acta fixtures.
 */
class StructuredActaDeterministicToolsTest {

    private static final String ACTA5_ID = "acta-5-doc";
    private static final String ACTA1_ID = "acta-1-doc";
    private static final String ACTA6_ID = "acta-6-doc";

    private MetadataMinuteDocumentService metadataService;
    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataLlmResponseCacheService llmCache;

    private Map<String, Map<String, Object>> actaById;

    @BeforeEach
    void setUp() throws IOException {
        metadataService =
                new MetadataMinuteDocumentService(
                        mock(PgVectorStore.class),
                        mock(ChatClient.class),
                        mock(JdbcTemplate.class),
                        400);
        actaById = new LinkedHashMap<>();
        loadActa("ACTA 1.pdf", "acta-1.txt", ACTA1_ID);
        loadActa("ACTA 2.pdf", "acta-2.txt", "acta-2-doc");
        loadActa("ACTA 3.pdf", "acta-3.txt", "acta-3-doc");
        loadActa("ACTA 5.pdf", "acta-5.txt", ACTA5_ID);
        loadActa("ACTA 6.pdf", "acta-6.txt", ACTA6_ID);

        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        when(llmCache.getCachedResponse(anyString(), anyString())).thenReturn("");
        when(retriever.retrieve(anyString())).thenAnswer(inv -> allDocs());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class)))
                .thenAnswer(inv -> allDocs());
    }

    @Test
    void getField_presidentByDate_returnsStructuredPresident() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Quién presidió la reunión del 25 de febrero de 2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("Jorge Moreno Navarro");
        assertThat(result.result()).contains("ACTA 5.pdf");
    }

    @Test
    void getField_participantsByDate_listsAllFromMetadata() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Quiénes asistieron al acta del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("participantes");
        assertThat(result.result()).contains("25/02/2026");
        assertThat(result.result()).doesNotContain(StructuredMinuteMetadataSupport.INCOMPLETE_EXTRACTION_NOTICE);
        assertThat(result.result()).contains("ACTA 5.pdf");
    }

    @Test
    void getField_fdGf01_participantCount_includesSlashDate() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("17", "25/02/2026", "ACTA 5");
        assertThat(result.result()).doesNotContain("25 de febrero de 2026");
    }

    @Test
    void getField_fdGf05_attendeeCount_includesSlashDate() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa("acta-2-doc"));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos asistentes hubo en la reunión del 25/02/2025?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("20", "25/02/2025", "ACTA 2");
    }

    @Test
    void getField_participantCount_usesNumberOfAttendees() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos participantes hubo en el acta del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("17");
        assertThat(result.result()).contains("25/02/2026");
        assertThat(result.result()).contains("ACTA 5");
    }

    @Test
    void getField_topicsByDate_usesStructuredTopics() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué temas se trataron en el acta del 25/08/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result().toLowerCase()).contains("eléctric");
    }

    @Test
    void getDuration_acta5_returnsNinetyMinutes() {
        MetadataGetDurationTool tool = new MetadataGetDurationTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuál fue la duración del acta del 25/02/2026?",
                                QueryType.GET_DURATION,
                                null));

        assertThat(result.result()).containsAnyOf("90", "1 hora y 30", "1 hour 30");
        assertThat(result.result()).contains("19:00", "20:30");
    }

    @Test
    void getDuration_fdGd01_stageAQuery_includesDateTimesAndNinetyMinutes() {
        MetadataGetDurationTool tool = new MetadataGetDurationTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Duración de la reunión del 25 de febrero de 2026.",
                                QueryType.GET_DURATION,
                                null));

        String answer = result.result();
        assertThat(answer).contains("19:00", "20:30", "90");
        assertThat(answer.toLowerCase(Locale.ROOT)).contains("febrero");
        assertThat(answer).containsAnyOf("1 hora y 30 minutos", "90 minutos");
    }

    @Test
    void count_startTime1900_countsThreeActas() {
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones comenzaron a las 19:00 horas?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        assertThat(result.result()).contains("3");
    }

    @Test
    void list_startTime1900_listsThreeActasWithIdsDatesAndSources() {
        MetadataFilterAndListTool tool = new MetadataFilterAndListTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué actas tienen hora de inicio a las 19:00?",
                                QueryType.FILTER_AND_LIST,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(result.result()).contains("3");
        assertThat(result.result()).contains("24/02/2025", "25/02/2025", "25/02/2026");
        assertThat(answer).contains("acta 1", "acta 2", "acta 5");
        assertThat(result.result()).contains("ACTA 1.pdf", "ACTA 2.pdf", "ACTA 5.pdf");
        assertThat(result.result()).doesNotContain("25/08/2025", "25/08/2026", "ACTA 6.pdf", "ACTA 3.pdf");
    }

    @Test
    void list_startTime1900_dedupesChunksToOneActa() {
        MetadataFilterAndListTool tool = new MetadataFilterAndListTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get(ACTA5_ID);
        List<Document> chunks =
                List.of(
                        toDoc(ACTA5_ID, meta, "header chunk"),
                        toDoc(ACTA5_ID, meta, "participants chunk"),
                        toDoc(ACTA5_ID, meta, "agenda chunk"));
        stubRetriever(chunks);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué actas tienen hora de inicio a las 19:00?",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result()).contains("Hay 1 acta");
        assertThat(result.result()).contains("ACTA 5");
        assertThat(result.result()).doesNotContain("Hay 3 actas");
    }

    @Test
    void count_year2025_countsThreeMeetings() {
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones hubo en 2025?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        assertThat(result.result()).contains("3");
        assertThat(result.result()).contains("24/02/2025", "25/02/2025", "25/08/2025");
        assertThat(result.result()).contains("ACTA 1", "ACTA 2", "ACTA 3");
    }

    @Test
    void count_year2025_dedupesHybridChunkDuplicates() {
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        List<Document> docs = new ArrayList<>();
        docs.addAll(chunkVariantsForActa(ACTA1_ID, actaById.get(ACTA1_ID), "hybrid-a1", 3));
        docs.addAll(chunkVariantsForActa("acta-2-doc", actaById.get("acta-2-doc"), "hybrid-a2", 2));
        docs.addAll(chunkVariantsForActa("acta-3-doc", actaById.get("acta-3-doc"), "hybrid-a3", 2));
        docs.addAll(chunkVariantsForActa(ACTA5_ID, actaById.get(ACTA5_ID), "hybrid-a5", 2));
        docs.addAll(chunkVariantsForActa(ACTA6_ID, actaById.get(ACTA6_ID), "hybrid-a6", 2));
        stubRetriever(docs);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones hubo en 2025?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        assertThat(result.result()).contains("Hubo 3 reuniones en 2025");
        assertThat(result.result()).doesNotContain("Hubo 7", "Hubo 5", "Hubo 4");
        assertThat(result.result()).contains("ACTA 1.pdf", "ACTA 2.pdf", "ACTA 3.pdf");
        assertThat(result.result()).doesNotContain("ACTA 5.pdf", "ACTA 6.pdf");
    }

    @Test
    void count_topicConvivencia_listsMultipleActas() {
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas actas mencionan normas de convivencia?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        assertThat(result.result()).matches(".*[1-9].*");
    }

    @Test
    void getField_jorgeRoleOnActa6_returnsAttendee() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                                QueryType.GET_FIELD,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).contains("jorge");
        assertThat(answer).containsAnyOf("asistente", "particip");
        assertThat(result.result()).containsAnyOf("25/08/2026", "25 de agosto de 2026", "2026-08-25");
        assertThat(result.result()).contains("Manuel Ortega Medina");
    }

    @Test
    void getField_jorgeRoleOnActa6_resolvesFullName() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("Jorge Moreno Navarro");
        assertThat(result.result()).contains("asistente");
        assertThat(result.result()).contains("presidencia recayó en Manuel Ortega Medina");
    }

    @Test
    void boolean_personOnDate_jorgeInActa6() {
        MetadataBooleanQueryTool tool = new MetadataBooleanQueryTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Asistió Jorge Moreno Navarro al acta del 25/08/2026?",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result()).containsIgnoringCase("sí");
        assertThat(result.result()).contains("ACTA 6.pdf");
    }

    @Test
    void boolean_topicOnDate_electricalActa6() {
        MetadataBooleanQueryTool tool = new MetadataBooleanQueryTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Se habló de problemas eléctricos en el acta del 25/08/2026?",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result()).isNotBlank();
        assertThat(result.result().toLowerCase()).doesNotContain("no consta");
    }

    @Test
    void filterAndList_augustMeetings_listsActas() {
        MetadataFilterAndListTool tool = new MetadataFilterAndListTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Lista las reuniones celebradas en agosto de 2025",
                                QueryType.FILTER_AND_LIST,
                                null));

        assertThat(result.result()).containsAnyOf("agosto", "2025-08-25", "ACTA 3.pdf");
    }

    @Test
    void futureDate_returnsNoActaWithoutFalseConsta() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Quién presidió el acta del 25/08/2030?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("No existe la reunión del 25 de agosto de 2030");
        assertThat(result.result().toLowerCase()).doesNotContain("no consta");
    }

    @Test
    void partialAttendees_rejectsConfidentPartialList() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> partial = new LinkedHashMap<>();
        partial.put("filename", "ACTA 5.pdf");
        partial.put("date_iso", "2026-02-25");
        partial.put("date", "25 de febrero de 2026");
        partial.put("president", "Jorge Moreno Navarro");
        partial.put("attendees", List.of("Ana Uno", "Bob Dos", "Car Tres"));
        partial.put("numberOfAttendees", 17);
        partial.put(
                "fieldPresence",
                Map.of("attendees", true, "numberOfAttendees", true, "date_iso", true, "president", true));
        stubRetriever(List.of(toDoc(ACTA5_ID, partial, "")));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Quiénes asistieron al acta del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains(StructuredMinuteMetadataSupport.INCOMPLETE_EXTRACTION_NOTICE);
        assertThat(result.result()).doesNotContain("Ana Uno, Bob Dos");
    }

    @Test
    void getField_enumerateAttendees_acta5_listsAllSeventeen() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Enumera todos los asistentes a la reunión del 25/02/2026.",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("Laura Díaz Castro", "Natalia Vázquez Gutiérrez", "Marta González Ramírez");
        assertThat(result.result()).contains("17 en total");
        assertThat(result.result()).doesNotContain(StructuredMinuteMetadataSupport.INCOMPLETE_EXTRACTION_NOTICE);
    }

    @Test
    void getField_participantCount_acta3_dedupesHybridChunks() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        List<Document> chunks =
                List.of(
                        toDoc("acta-3-doc", actaById.get("acta-3-doc"), "chunk-a"),
                        toDoc("acta-3-doc", actaById.get("acta-3-doc"), "chunk-b"));
        stubRetriever(chunks);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos participantes asistieron a la reunión del 25 de agosto de 2025?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("18");
        assertThat(result.result()).contains("25/08/2025");
        assertThat(result.result()).doesNotContain("25 de agosto de 2025");
        assertThat(result.result()).doesNotContain("Beatriz Suárez Aguilar, Beatriz Suárez Aguilar");
    }

    @Test
    void boolean_fewerThanTenParticipants_returnsNo() {
        MetadataBooleanQueryTool tool = new MetadataBooleanQueryTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Hay actas con menos de 10 participantes?",
                                QueryType.BOOLEAN_QUERY,
                                null));

        assertThat(result.result().toLowerCase(Locale.ROOT)).startsWith("no");
        assertThat(result.result()).contains("17");
    }

    @Test
    void countAndExplain_exact21_returnsDeterministicCorpusNegative() {
        MetadataCountAndExplainTool tool = new MetadataCountAndExplainTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?",
                                QueryType.COUNT_AND_EXPLAIN,
                                null));

        assertThat(result.result()).contains("21");
        assertThat(result.result().toLowerCase(Locale.ROOT))
                .contains("no existen registros")
                .containsAnyOf("17", "18", "19", "20");
        assertThat(result.result().toLowerCase(Locale.ROOT))
                .doesNotContain("¿a qué acta", "se decidió aprobar");
    }

    @Test
    void countAndExplain_topicOccurrence_calefaccion_countsOneActa() {
        MetadataCountAndExplainTool tool = new MetadataCountAndExplainTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Cuántas veces aparece la calefacción y en qué contexto fue tratada.",
                                QueryType.COUNT_AND_EXPLAIN,
                                null));

        assertThat(result.result().toLowerCase(Locale.ROOT)).contains("una vez");
        assertThat(result.result()).contains("25/02/2026");
        assertThat(result.result()).contains("ACTA 5");
        assertThat(result.result().toLowerCase(Locale.ROOT)).contains("presupuesto");
        assertThat(result.result().toLowerCase(Locale.ROOT))
                .doesNotContain("acta 1", "acta 2", "acta 3", "acta 6", "videovigilancia");
    }

    @Test
    void getField_accentedRoleQuery_resolvesAttendeeRole() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                                QueryType.GET_FIELD,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).contains("jorge");
        assertThat(answer).containsAnyOf("asistente", "particip");
    }

    @Test
    void getField_cuantosAsistieron_returnsCountNotNameList() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos asistieron al acta del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("17");
        assertThat(result.result()).contains("participantes");
        assertThat(result.result()).doesNotContain("Laura Díaz Castro,");
    }

    @Test
    void getField_strictDateFilter_rejectsSameMonthDifferentDay() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Quiénes asistieron al acta del 25/02/2025?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("20");
        assertThat(result.result()).doesNotContain("ACTA 1.pdf");
        assertThat(result.result()).contains("ACTA 2.pdf");
    }

    @Test
    void getField_acta1_twentyAttendeeCount_withoutIncompleteNotice() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA1_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos participantes hubo en el acta del 24/02/2025?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("20");
        assertThat(result.result()).doesNotContain(StructuredMinuteMetadataSupport.INCOMPLETE_EXTRACTION_NOTICE);
    }

    @Test
    void getField_hybridChunks_isabelCastroAlias_fdGf01_returns17() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get(ACTA5_ID);
        @SuppressWarnings("unchecked")
        List<String> withAlias = new ArrayList<>((List<String>) meta.get("attendees"));
        withAlias.add("Isabel Castro");
        Map<String, Object> aliasMeta = new LinkedHashMap<>(meta);
        aliasMeta.put("attendees", withAlias);

        List<Document> hybrid =
                List.of(
                        toDoc(ACTA5_ID + "-chunk-0", meta, "header chunk"),
                        toDoc(ACTA5_ID + "-chunk-1", aliasMeta, "participants chunk"));
        stubRetriever(hybrid);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("17", "25/02/2026", "ACTA 5");
        assertThat(result.result()).doesNotContain("18 participantes");
    }

    @Test
    void getField_hybridChunks_isabelCastroAlias_fdGf03_listsSeventeen() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get(ACTA5_ID);
        @SuppressWarnings("unchecked")
        List<String> withAlias = new ArrayList<>((List<String>) meta.get("attendees"));
        withAlias.add("Isabel Castro");
        Map<String, Object> aliasMeta = new LinkedHashMap<>(meta);
        aliasMeta.put("attendees", withAlias);

        List<Document> hybrid =
                List.of(
                        toDoc(ACTA5_ID + "-chunk-0", meta, "header chunk"),
                        toDoc(ACTA5_ID + "-chunk-1", aliasMeta, "participants chunk"));
        stubRetriever(hybrid);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Enumera todos los asistentes a la reunión del 25/02/2026.",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("17 en total");
        assertThat(result.result()).contains("Isabel Castro Torres");
        assertThat(result.result()).doesNotContain("Isabel Castro (18");
        assertThat(result.result()).doesNotContain("Isabel Castro,");
        assertThat(result.result()).doesNotContain("18 en total");
    }

    @Test
    void getField_fdGf06_spanishLongDateWithDelYear_twentyNamesPreserved() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get("acta-2-doc");
        List<Document> hybrid =
                List.of(
                        toDoc("acta-2-chunk-0", meta, "chunk-a"),
                        toDoc("acta-2-chunk-1", meta, "chunk-b"));
        stubRetriever(hybrid);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "dime los asistentes del acta del 25 de febrero del 2025",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("20 en total");
    }

    @Test
    void getField_fdGf06_hybridChunks_twentyNamesPreserved() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get("acta-2-doc");
        List<Document> hybrid =
                List.of(
                        toDoc("acta-2-chunk-0", meta, "chunk-a"),
                        toDoc("acta-2-chunk-1", meta, "chunk-b"));
        stubRetriever(hybrid);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Enumera todos los asistentes a la reunión del 25/02/2025.",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("20 en total");
        assertThat(result.result()).contains("Antonio Martínez López", "Natalia Vázquez Gutiérrez");
    }

    @Test
    void getField_absentStructuredAttendees_fallsBackToTextExtraction() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> sparse = new LinkedHashMap<>();
        sparse.put("filename", "ACTA 5.pdf");
        sparse.put("date_iso", "2026-02-25");
        sparse.put("president", "Jorge Moreno Navarro");
        Document doc = toDoc(ACTA5_ID, sparse, "acta body with attendee section");
        stubRetriever(List.of(doc));
        when(extractor.extractAttendees("acta body with attendee section"))
                .thenReturn(List.of("Ana Sánchez Herrera", "Laura Díaz Castro"));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Enumera todos los asistentes a la reunión del 25/02/2026.",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("Ana Sánchez Herrera", "Laura Díaz Castro");
    }

    @Test
    void getField_hybridChunks_enumerateActa5_listsAllSeventeen() {
        MetadataGetFieldTool tool = new MetadataGetFieldTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get(ACTA5_ID);
        Document headerOnly = toDoc(ACTA5_ID, stripAttendees(meta), "header chunk");
        Document attendeesChunk = toDoc(ACTA5_ID, meta, "participants chunk");
        List<Document> hybrid = List.of(headerOnly, attendeesChunk);
        when(retriever.retrieve(anyString())).thenReturn(hybrid);
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(hybrid);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Enumera todos los asistentes a la reunión del 25/02/2026.",
                                QueryType.GET_FIELD,
                                null));

        assertThat(result.result()).contains("Laura Díaz Castro", "Natalia Vázquez Gutiérrez");
        assertThat(result.result()).contains("17 en total");
        assertThat(result.result()).doesNotContain(StructuredMinuteMetadataSupport.INCOMPLETE_EXTRACTION_NOTICE);
    }

    private static Map<String, Object> stripAttendees(Map<String, Object> meta) {
        Map<String, Object> copy = new LinkedHashMap<>(meta);
        copy.remove("attendees");
        copy.put("numberOfAttendees", 17);
        return copy;
    }

    @Test
    void summarizeMeeting_briefActa5_usesStructuredMetadata() {
        MetadataSummarizeMeetingTool tool = new MetadataSummarizeMeetingTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA5_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Resume brevemente el acta del 25/02/2026.",
                                QueryType.SUMMARIZE_MEETING,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).contains("25/02/2026", "17", "19:00", "20:30");
        assertThat(answer).containsAnyOf("calefacc", "plagas", "convivencia");
        assertThat(answer).doesNotContain("radiación solar", "acta del 25 de agosto de 2026");
    }

    @Test
    void summarizeMeeting_briefActa5_doesNotSummarizeWrongActa() {
        MetadataSummarizeMeetingTool tool = new MetadataSummarizeMeetingTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(docsForActa(ACTA6_ID));

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Resume brevemente el acta del 25/02/2026.",
                                QueryType.SUMMARIZE_MEETING,
                                null));

        String answer = result.result().toLowerCase(Locale.ROOT);
        assertThat(answer).doesNotContain("videovigilancia", "25/08/2026", "19 asistentes");
    }

    @Test
    void summarizeMeeting_year2030_returnsDeterministicNegative() {
        MetadataSummarizeMeetingTool tool = new MetadataSummarizeMeetingTool(chatClient, retriever, extractor, llmCache);
        stubRetriever(allDocs());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Resume el acta del año 2030.",
                                QueryType.SUMMARIZE_MEETING,
                                null));

        assertThat(result.result()).contains("2030");
        assertThat(result.result().toLowerCase(Locale.ROOT))
                .doesNotContain("reunión del 25 de febrero de 2026", "acta de la reunión de la comunidad");
    }

    @Test
    void dedupe_multipleChunks_countsOneActa() {
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        Map<String, Object> meta = actaById.get(ACTA5_ID);
        List<Document> chunks =
                List.of(
                        toDoc(ACTA5_ID, meta, "header chunk"),
                        toDoc(ACTA5_ID, meta, "participants chunk"),
                        toDoc(ACTA5_ID, meta, "agenda chunk"));
        stubRetriever(chunks);

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿Cuántas reuniones comenzaron a las 19:00 horas?",
                                QueryType.COUNT_DOCUMENTS,
                                null));

        assertThat(result.result()).containsAnyOf("Una reunión", "1 reunión", "1 meeting");
        assertThat(result.result()).doesNotContain("3 reuniones");
    }

    @Test
    void structuredSupport_flattensNestedActa() {
        Map<String, Object> outer = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("president", "Test President");
        nested.put("date_iso", "2026-02-25");
        outer.put("structuredActa", nested);

        Map<String, Object> flat = StructuredMinuteMetadataSupport.flattenMetadata(outer);
        assertThat(flat.get("president")).isEqualTo("Test President");
        assertThat(StructuredMinuteMetadataSupport.resolveDate(flat)).isEqualTo("2026-02-25");
    }

    private void loadActa(String filename, String fixture, String docId) throws IOException {
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

    private List<Document> docsForActa(String docId) {
        return List.of(toDoc(docId, actaById.get(docId), "acta body"));
    }

    private Document toDoc(String docId, Map<String, Object> meta, String text) {
        Map<String, Object> chunkMeta = new LinkedHashMap<>();
        chunkMeta.put("document_id", docId);
        StructuredMinuteMetadataSupport.flattenMetadata(meta).forEach(chunkMeta::put);
        return new Document(text, chunkMeta);
    }

    private List<Document> chunkVariantsForActa(
            String canonicalDocId, Map<String, Object> meta, String chunkPrefix, int chunkCount) {
        List<Document> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(toDoc(chunkPrefix + "-chunk-" + i, meta, "chunk body " + i + " for " + canonicalDocId));
        }
        return chunks;
    }

    private void stubRetriever(List<Document> docs) {
        when(retriever.retrieve(anyString())).thenReturn(docs);
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(docs);
    }
}
