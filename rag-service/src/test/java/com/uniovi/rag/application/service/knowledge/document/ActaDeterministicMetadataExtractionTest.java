package com.uniovi.rag.application.service.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import java.util.UUID;
import org.mockito.Mockito;

/**
 * Deterministic acta metadata extraction for all corpus ACTA documents (no LLM).
 */
class ActaDeterministicMetadataExtractionTest {

    private MetadataMinuteDocumentService service;

    @BeforeEach
    void setUp() {
        service = new MetadataMinuteDocumentService(
                Mockito.mock(PgVectorStore.class),
                Mockito.mock(ChatClient.class),
                Mockito.mock(JdbcTemplate.class),
                400);
    }

    static Stream<Arguments> actaFixtures() {
        return Stream.of(
                Arguments.of("ACTA 1.pdf", "acta-1.txt", "2025-02-24", "Juan Pérez Gutiérrez", 20, "19:00", "20:30", 90),
                Arguments.of("ACTA 2.pdf", "acta-2.txt", "2025-02-25", "Antonio Martínez López", 20, "19:00", "20:45", 105),
                Arguments.of("ACTA 3.pdf", "acta-3.txt", "2025-08-25", "Beatriz Suárez Aguilar", 18, "19:30", "21:00", 90),
                Arguments.of("ACTA 5.pdf", "acta-5.txt", "2026-02-25", "Jorge Moreno Navarro", 17, "19:00", "20:30", 90),
                Arguments.of("ACTA 6.pdf", "acta-6.txt", "2026-08-25", "Manuel Ortega Medina", 19, "19:15", "20:45", 90));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actaFixtures")
    void extractsStructuredMetadataPerActa(
            String filename,
            String fixture,
            String expectedDateIso,
            String expectedPresident,
            int expectedAttendeeCount,
            String expectedStart,
            String expectedEnd,
            int expectedDurationMinutes)
            throws IOException {
        String content = loadFixture(fixture);
        assertThat(MetadataMinuteDocumentService.looksLikeActaDocument(content)).isTrue();

        Optional<Map<String, Object>> extracted =
                service.tryExtractDeterministicMetadataForIndexing(content, filename, "doc-" + fixture);

        assertThat(extracted).isPresent();
        Map<String, Object> meta = extracted.get();

        assertThat(meta.get("date_iso")).isEqualTo(expectedDateIso);
        assertThat(meta.get("president")).isEqualTo(expectedPresident);
        assertThat(meta.get("numberOfAttendees")).isEqualTo(expectedAttendeeCount);
        assertThat(meta.get("attendeesCount")).isEqualTo(expectedAttendeeCount);
        assertThat(meta.get("startTime").toString()).contains(expectedStart);
        assertThat(meta.get("endTime").toString()).contains(expectedEnd);
        assertThat(meta.get("durationMinutes")).isEqualTo(expectedDurationMinutes);
        assertThat(meta.get("sourceTitle")).isEqualTo(filename);
        assertThat(meta.get("filename")).isEqualTo(filename);

        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) meta.get("attendees");
        assertThat(attendees).isNotEmpty().contains(expectedPresident);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> fieldPresence = (Map<String, Boolean>) meta.get("fieldPresence");
        assertThat(fieldPresence)
                .containsEntry("date_iso", true)
                .containsEntry("president", true)
                .containsEntry("startTime", true)
                .containsEntry("endTime", true)
                .containsEntry("durationMinutes", true)
                .containsEntry("numberOfAttendees", true)
                .containsEntry("attendees", true);

        @SuppressWarnings("unchecked")
        Map<String, String> agenda = (Map<String, String>) meta.get("agenda");
        assertThat(agenda).isNotEmpty();
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) meta.get("topics");
        assertThat(topics).isNotEmpty();
        @SuppressWarnings("unchecked")
        List<String> sections = (List<String>) meta.get("sections");
        assertThat(sections).contains("Fecha", "Asistentes", "Orden del día");
    }

    @Test
    void presidentAndParticipantsSurviveWhenContentIsSplitAcrossChunks() throws IOException {
        String content = loadFixture("acta-1.txt");
        int splitAt = content.indexOf("• Juan Pérez");
        assertThat(splitAt).isGreaterThan(100);

        String chunk0 = content.substring(0, splitAt);

        Optional<Map<String, Object>> full =
                service.tryExtractDeterministicMetadataForIndexing(content, "ACTA 1.pdf", "acta-1-full");
        Optional<Map<String, Object>> fromChunk0 =
                service.tryExtractDeterministicMetadataForIndexing(chunk0, "ACTA 1.pdf", "acta-1-chunk0");

        assertThat(full).isPresent();
        assertThat(full.get().get("president")).isEqualTo("Juan Pérez Gutiérrez");

        // Chunk 0 alone loses president/attendee names (the chunking problem).
        assertThat(fromChunk0).isPresent();
        assertThat(fromChunk0.get().get("president")).isNull();
        @SuppressWarnings("unchecked")
        List<String> chunk0Attendees = (List<String>) fromChunk0.get().get("attendees");
        assertThat(chunk0Attendees).isEmpty();

        // Index-time merge copies full-document acta fields onto every vector chunk.
        Map<String, Object> actaMeta = full.get();
        Map<String, Object> chunkMeta = KnowledgeChunkMetadataFactory.buildV2(
                CorpusScope.PROJECT_SHARED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "sig",
                "ACTA 1.pdf",
                1,
                5,
                "hash");
        KnowledgeChunkMetadataFactory.mergeActaStructuredFields(chunkMeta, actaMeta);

        assertThat(chunkMeta.get("president")).isEqualTo("Juan Pérez Gutiérrez");
        assertThat(chunkMeta.get("date_iso")).isEqualTo("2025-02-24");
        assertThat(chunkMeta.get("fieldPresence")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        List<String> mergedAttendees = (List<String>) chunkMeta.get("attendees");
        assertThat(mergedAttendees).contains("Juan Pérez Gutiérrez");
    }

    @Test
    void aggregatesParticipantCountsDeduplicatedByActa() throws IOException {
        List<Map<String, Object>> perActa = new ArrayList<>();
        for (Arguments args : actaFixtures().toList()) {
            String filename = (String) args.get()[0];
            String fixture = (String) args.get()[1];
            String docId = "stable-" + fixture;
            service.tryExtractDeterministicMetadataForIndexing(loadFixture(fixture), filename, docId)
                    .ifPresent(perActa::add);
        }
        assertThat(perActa).hasSize(5);

        Map<String, Integer> countByActa = new LinkedHashMap<>();
        int totalParticipants = 0;
        for (Map<String, Object> meta : perActa) {
            String actaKey = meta.get("document_id") != null
                    ? meta.get("document_id").toString()
                    : Objects.toString(meta.get("id"), "unknown");
            int count = ((Number) meta.get("numberOfAttendees")).intValue();
            countByActa.putIfAbsent(actaKey, count);
            totalParticipants += countByActa.get(actaKey);
        }
        assertThat(countByActa).hasSize(5);
        assertThat(totalParticipants).isEqualTo(20 + 20 + 18 + 17 + 19);
    }

    @Test
    void supportsEvaluationQuestionsFromStructuredMetadata() throws IOException {
        List<Map<String, Object>> all = loadAllActaMetadata();

        // Q1: 17 participants on 25/02/2026 (ACTA 5)
        assertThat(findByDateIso(all, "2026-02-25").get("numberOfAttendees")).isEqualTo(17);

        // Q3/Q5: ACTA 5 summary signals and duration 19:00-20:30
        Map<String, Object> acta5 = findByDateIso(all, "2026-02-25");
        assertThat(acta5.get("startTime").toString()).contains("19:00");
        assertThat(acta5.get("endTime").toString()).contains("20:30");
        assertThat(acta5.get("durationMinutes")).isEqualTo(90);

        // Q6: actas with start time 19:00
        List<String> actasAt1900 = all.stream()
                .filter(m -> m.get("startTime").toString().contains("19:00"))
                .map(m -> (String) m.get("filename"))
                .sorted()
                .toList();
        assertThat(actasAt1900).containsExactly("ACTA 1.pdf", "ACTA 2.pdf", "ACTA 5.pdf");

        // Q7: Juan Pérez in multiple actas
        long juanActaCount = all.stream()
                .filter(m -> containsPerson(m, "Juan Pérez"))
                .count();
        assertThat(juanActaCount).isGreaterThanOrEqualTo(2);

        // Q10: Jorge attendee on 25/08/2026 (ACTA 6, not president)
        Map<String, Object> acta6 = findByDateIso(all, "2026-08-25");
        assertThat(acta6.get("president")).isEqualTo("Manuel Ortega Medina");
        assertThat(containsPerson(acta6, "Jorge Moreno Navarro")).isTrue();

        // Q11: common sections across all actas
        Set<String> commonSections = Set.of("Fecha", "Asistentes", "Orden del día");
        for (Map<String, Object> meta : all) {
            @SuppressWarnings("unchecked")
            List<String> sections = (List<String>) meta.get("sections");
            assertThat(sections).containsAll(commonSections);
        }

        // Q12: 3 meetings in 2025
        long meetings2025 = all.stream()
                .filter(m -> m.get("date_iso").toString().startsWith("2025"))
                .count();
        assertThat(meetings2025).isEqualTo(3);

        // Q15: normas de convivencia
        List<String> convivenciaActas = all.stream()
                .filter(this::mentionsConvivencia)
                .map(m -> (String) m.get("filename"))
                .sorted()
                .toList();
        assertThat(convivenciaActas).contains("ACTA 1.pdf", "ACTA 3.pdf", "ACTA 5.pdf");

        // Q16: electrical problems (ACTA 6)
        Map<String, Object> electrical = findByDateIso(all, "2026-08-25");
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) electrical.get("topics");
        assertThat(topics).anyMatch(t -> t.toLowerCase().contains("eléctric"));
    }

    @Test
    void extractsBudgetMentionsWhenPresent() throws IOException {
        Map<String, Object> acta1 =
                service.tryExtractDeterministicMetadataForIndexing(
                                loadFixture("acta-1.txt"), "ACTA 1.pdf", "acta-1-budget")
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<String> budgets = (List<String>) acta1.get("budgetMentions");
        assertThat(budgets).isNotEmpty();
        assertThat(String.join(" ", budgets).toLowerCase()).contains("presupuesto");

        @SuppressWarnings("unchecked")
        Map<String, Boolean> presence = (Map<String, Boolean>) acta1.get("fieldPresence");
        assertThat(presence.get("budgetMentions")).isTrue();
    }

    private List<Map<String, Object>> loadAllActaMetadata() throws IOException {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Arguments args : actaFixtures().toList()) {
            String filename = (String) args.get()[0];
            String fixture = (String) args.get()[1];
            service.tryExtractDeterministicMetadataForIndexing(loadFixture(fixture), filename, "id-" + fixture)
                    .ifPresent(all::add);
        }
        return all;
    }

    private Map<String, Object> findByDateIso(List<Map<String, Object>> all, String dateIso) {
        return all.stream()
                .filter(m -> dateIso.equals(m.get("date_iso")))
                .findFirst()
                .orElseThrow();
    }

    private boolean containsPerson(Map<String, Object> meta, String name) {
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) meta.get("attendees");
        if (attendees != null && attendees.stream().anyMatch(a -> a.contains(name))) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<String> named = (List<String>) meta.get("namedPeople");
        return named != null && named.stream().anyMatch(a -> a.contains(name));
    }

    private boolean mentionsConvivencia(Map<String, Object> meta) {
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) meta.get("topics");
        if (topics != null && topics.stream().anyMatch(t -> t.toLowerCase().contains("convivencia"))) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> agenda = (Map<String, String>) meta.get("agenda");
        if (agenda != null) {
            return agenda.keySet().stream().anyMatch(k -> k.toLowerCase().contains("convivencia"));
        }
        return false;
    }

    private static String loadFixture(String name) throws IOException {
        Path path = Path.of("src/test/resources/acta-fixtures", name);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
