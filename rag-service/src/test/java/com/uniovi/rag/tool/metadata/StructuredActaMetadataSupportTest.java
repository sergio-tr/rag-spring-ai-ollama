package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.Minute;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StructuredMinuteMetadataSupportTest {

    @Test
    void flattenMetadata_mergesNestedStructuredActa() {
        Map<String, Object> outer = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("president", "Presidente Test");
        nested.put("date_iso", "2025-02-24");
        outer.put("structuredActa", nested);
        outer.put("document_id", "doc-1");

        Map<String, Object> flat = StructuredMinuteMetadataSupport.flattenMetadata(outer);
        assertThat(flat.get("president")).isEqualTo("Presidente Test");
        assertThat(flat.get("document_id")).isEqualTo("doc-1");
    }

    @Test
    void isFutureOrUnavailableDate_detects2030() {
        assertThat(StructuredMinuteMetadataSupport.isFutureOrUnavailableDate("2030-08-25")).isTrue();
        assertThat(StructuredMinuteMetadataSupport.isFutureOrUnavailableDate("2026-02-25")).isFalse();
    }

    @Test
    void isAttendeesListComplete_rejectsPartialList() {
        Map<String, Object> meta = Map.of("numberOfAttendees", 17, "attendees", List.of("A", "B", "C"));
        Minute minute =
                new Minute(
                        "id",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("A", "B", "C"),
                        17,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        assertThat(StructuredMinuteMetadataSupport.isAttendeesListComplete(meta, minute)).isFalse();
    }

    @Test
    void dedupeRichnessScore_prefersRicherMetadata() {
        Map<String, Object> sparse = Map.of("date_iso", "2025-02-24");
        Map<String, Object> rich =
                Map.of(
                        "date_iso",
                        "2025-02-24",
                        "president",
                        "Juan Pérez",
                        "attendees",
                        List.of("A", "B", "C"),
                        "numberOfAttendees",
                        3);
        assertThat(StructuredMinuteMetadataSupport.richnessScore(rich))
                .isGreaterThan(StructuredMinuteMetadataSupport.richnessScore(sparse));
    }

    @Test
    void formatDateSlash_convertsIsoAndSpanish() {
        Minute isoMinute =
                new Minute(
                        "id",
                        "ACTA 1.pdf",
                        "2025-02-24",
                        null,
                        "19:00",
                        null,
                        null,
                        null,
                        List.of(),
                        0,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        assertThat(StructuredMinuteMetadataSupport.formatDateSlash(isoMinute)).isEqualTo("24/02/2025");
        assertThat(StructuredMinuteMetadataSupport.formatDateSlash("25 de febrero de 2026")).isEqualTo("25/02/2026");
    }

    @Test
    void resolveCanonicalSlashDate_convertsSpanishAndIso_withoutInventingDates() {
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate("2026-02-25")).isEqualTo("25/02/2026");
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate("25 de agosto de 2025"))
                .isEqualTo("25/08/2025");
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate("25/02/2025")).isEqualTo("25/02/2025");
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate("fecha desconocida")).isEmpty();
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate((String) null)).isEmpty();
    }

    @Test
    void resolveCanonicalSlashDate_fromMinute_usesStoredDateOnly() {
        Minute spanishDateMinute =
                new Minute(
                        "id",
                        "ACTA 3.pdf",
                        "25 de agosto de 2025",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        18,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate(spanishDateMinute))
                .isEqualTo("25/08/2025");
    }

    @Test
    void resolveCanonicalSlashDate_fromMinute_fallsBackToSummaryWhenDateMissing() {
        Minute summaryOnly =
                new Minute(
                        "id",
                        "ACTA 2.pdf",
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        20,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "Fecha: 25 de febrero de 2025\nLugar: Sala de reuniones");
        assertThat(StructuredMinuteMetadataSupport.resolveCanonicalSlashDate(summaryOnly))
                .isEqualTo("25/02/2025");
    }

    @Test
    void formatFindParagraphTopicEvidenceAnswer_anchorsUndatedMinuteViaSummary() {
        Minute minute =
                new Minute(
                        "id",
                        "ACTA 2.pdf",
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        20,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "Fecha: 25 de febrero de 2025");
        String query = "¿Qué se dijo en relación a la limpieza de las zonas comunes?";
        String body =
                "Se plantea la necesidad de mejorar la limpieza en las zonas comunes. Se aprueba la contratación de un nuevo servicio de limpieza con mayor frecuencia.";
        String answer =
                StructuredMinuteMetadataSupport.formatFindParagraphTopicEvidenceAnswer(
                        query, minute, body, "Acta: 2025-02-25. Presidente: Antonio Martínez López.");
        assertThat(answer).startsWith("En el acta del 25/02/2025");
        assertThat(answer).contains("limpieza", "contratación");
    }

    @Test
    void formatStartTimeListAnswer_includesCountIdsDatesAndSources() {
        List<Minute> matching =
                List.of(
                        new Minute(
                                "a1",
                                "ACTA 1.pdf",
                                "2025-02-24",
                                null,
                                "19:00",
                                null,
                                null,
                                null,
                                List.of(),
                                0,
                                Map.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null),
                        new Minute(
                                "a2",
                                "ACTA 2.pdf",
                                "2025-02-25",
                                null,
                                "19:00",
                                null,
                                null,
                                null,
                                List.of(),
                                0,
                                Map.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null));
        String answer =
                StructuredMinuteMetadataSupport.formatStartTimeListAnswer(
                        "¿Qué actas tienen hora de inicio a las 19:00?", matching, "19:00");
        assertThat(answer).contains("Hay 2 actas", "ACTA 1 (24/02/2025)", "ACTA 2 (25/02/2025)", "ACTA 1.pdf");
    }

    @Test
    void formatDeterministicFilterListAnswer_fdFl03_includesSlashDateActaTopicAndCount() {
        Minute acta6 =
                new Minute(
                        "acta-6-doc",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        "19:15",
                        "20:45",
                        "Manuel Ortega Medina",
                        "Natalia Vázquez Gutiérrez",
                        List.of(),
                        19,
                        Map.of(),
                        List.of("videovigilancia"),
                        List.of(),
                        List.of(),
                        "Instalación de cámaras");

        Optional<String> answer =
                StructuredMinuteMetadataSupport.formatDeterministicFilterListAnswer(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        List.of(acta6),
                        "videovigilancia",
                        false);

        assertThat(answer)
                .hasValue(
                        "La reunión del 25/08/2026 (ACTA 6) trató videovigilancia y tuvo 19 asistentes.");
    }

    @Test
    void resolveAttendeeCount_prefersStructuredNumberOfAttendees() {
        Minute minute =
                new Minute(
                        "id",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("a", "b", "c"),
                        19,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        assertThat(StructuredMinuteMetadataSupport.resolveAttendeeCount(minute)).isEqualTo(19);
    }

    @Test
    void dedupeCanonicalAttendeeNames_dropsIsabelCastroAliasWhenFullNamePresent() {
        List<String> names =
                List.of(
                        "Isabel Castro Torres",
                        "Jorge Moreno Navarro",
                        "Isabel Castro",
                        "Laura Díaz Castro");

        List<String> deduped = StructuredMinuteMetadataSupport.dedupeCanonicalAttendeeNames(names);

        assertThat(deduped).containsExactly(
                "Isabel Castro Torres", "Jorge Moreno Navarro", "Laura Díaz Castro");
    }

    @Test
    void mergeCanonicalAttendeeLists_prefersCompleteStructuredListOverAliasUnion() {
        List<String> full =
                List.of(
                        "Jorge Moreno Navarro",
                        "Laura Díaz Castro",
                        "Manuel Ortega Medina",
                        "Rosa Aguilar Fernández",
                        "Ricardo Flores Sánchez",
                        "Beatriz Suárez Aguilar",
                        "Pedro Jiménez Suárez",
                        "Ana Sánchez Herrera",
                        "Patricia Navarro Díaz",
                        "Eduardo Rojas Martínez",
                        "Silvia Medina Pérez",
                        "Francisco Torres Delgado",
                        "Daniel Gutiérrez Moreno",
                        "Natalia Vázquez Gutiérrez",
                        "Antonio Martínez López",
                        "Isabel Castro Torres",
                        "Marta González Ramírez");
        List<String> withAlias = new ArrayList<>(full);
        withAlias.add("Isabel Castro");

        List<String> merged =
                StructuredMinuteMetadataSupport.mergeCanonicalAttendeeLists(full, withAlias, 17);

        assertThat(merged).hasSize(17);
        assertThat(merged).contains("Isabel Castro Torres").doesNotContain("Isabel Castro");
    }

    @Test
    void resolveAttendeeCount_prefersStructuredCountOverInflatedList() {
        List<String> inflated = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            inflated.add("Persona " + i);
        }
        inflated.add("Isabel Castro");
        Minute minute =
                new Minute(
                        "id",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        inflated,
                        17,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);

        assertThat(StructuredMinuteMetadataSupport.resolveAttendeeCount(minute)).isEqualTo(17);
    }

    @Test
    void resolvePersonRole_jorgeInActa6_isAttendee() {
        Minute acta6 =
                new Minute(
                        "acta-6-doc",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        "19:15",
                        "20:45",
                        "Manuel Ortega Medina",
                        "Natalia Vázquez Gutiérrez",
                        List.of("Jorge Moreno Navarro", "Laura Díaz Castro"),
                        19,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        Optional<StructuredMinuteMetadataSupport.ResolvedPersonRole> resolved =
                StructuredMinuteMetadataSupport.resolvePersonRole(acta6, "Jorge");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().canonicalName()).isEqualTo("Jorge Moreno Navarro");
        assertThat(resolved.get().role()).isEqualTo(StructuredMinuteMetadataSupport.PersonMeetingRole.ATTENDEE);
    }

    @Test
    void personNameMatches_partialFirstName() {
        assertThat(StructuredMinuteMetadataSupport.personNameMatches("Jorge Moreno Navarro", "Jorge")).isTrue();
        assertThat(StructuredMinuteMetadataSupport.personNameMatches("Manuel Ortega Medina", "Jorge")).isFalse();
    }

    @Test
    void formatPersonRoleAnswer_includesRoleDateAndSource() {
        Minute acta6 =
                new Minute(
                        "id",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        null,
                        null,
                        "Manuel Ortega Medina",
                        null,
                        List.of("Jorge Moreno Navarro"),
                        19,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        String answer =
                StructuredMinuteMetadataSupport.formatPersonRoleAnswer(
                        "¿Quién asistió Jorge en la reunión del 25/08/2026?",
                        "Jorge",
                        new StructuredMinuteMetadataSupport.ResolvedPersonRole(
                                "Jorge Moreno Navarro", StructuredMinuteMetadataSupport.PersonMeetingRole.ATTENDEE),
                        acta6);
        assertThat(answer).contains("Jorge Moreno Navarro", "asistente", "25/08/2026", "ACTA 6", "ACTA 6.pdf");
    }

    @Test
    void fdGf04_formatPersonRoleAnswer_includesPresidentContrast() {
        Minute acta6 =
                new Minute(
                        "id",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        null,
                        null,
                        "Manuel Ortega Medina",
                        null,
                        List.of("Jorge Moreno Navarro"),
                        19,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        String answer =
                StructuredMinuteMetadataSupport.formatPersonRoleAnswer(
                        "¿Qué papel tuvo Jorge Moreno Navarro en la reunión del 25/08/2026?",
                        "Jorge Moreno Navarro",
                        new StructuredMinuteMetadataSupport.ResolvedPersonRole(
                                "Jorge Moreno Navarro", StructuredMinuteMetadataSupport.PersonMeetingRole.ATTENDEE),
                        acta6);
        assertThat(answer)
                .contains("Jorge Moreno Navarro", "Manuel Ortega Medina", "asistente", "presidencia")
                .doesNotContainPattern("(?i)jorge.*presidente");
    }

    @Test
    void formatYearMeetingCountAnswer_includesCountIdsDatesAndSources() {
        List<Minute> matching =
                List.of(
                        new Minute(
                                "a1",
                                "ACTA 1.pdf",
                                "2025-02-24",
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                0,
                                Map.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null),
                        new Minute(
                                "a3",
                                "ACTA 3.pdf",
                                "2025-08-25",
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                0,
                                Map.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null));
        String answer =
                StructuredMinuteMetadataSupport.formatYearMeetingCountAnswer(
                        "¿Cuántas reuniones hubo en 2025?", matching, "2025");
        assertThat(answer).contains("Hubo 2 reuniones en 2025", "ACTA 1 (24/02/2025)", "ACTA 3 (25/08/2025)", "ACTA 1.pdf");
    }

    @Test
    void resolveActaLabel_extractsFromFilename() {
        Minute minute =
                new Minute(
                        "id",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        0,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null);
        assertThat(StructuredMinuteMetadataSupport.resolveActaLabel(minute)).isEqualTo("ACTA 5");
    }
}
