package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.Minute;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredMinuteMetadataSupportBriefSummaryTest {

    @Test
    void formatStructuredBriefMeetingSummary_fdSm01_includesMandatoryTokens() {
        Map<String, String> agenda = new LinkedHashMap<>();
        agenda.put("Lectura y aprobación del acta anterior", "aprobada por unanimidad");
        agenda.put("Evaluación del sistema de calefacción", "solicitar presupuestos para modernizar");
        agenda.put("Propuesta de nuevo reglamento de convivencia", "votar en próxima reunión");
        agenda.put("Problemas de plagas en el edificio", "contratar control urgente");
        agenda.put("Ruegos y preguntas", "mejorar señalización de salidas de emergencia");

        Minute minute =
                new Minute(
                        "acta-5-doc",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        "Sala de reuniones",
                        "19:00",
                        "20:30",
                        "Jorge Moreno Navarro",
                        "Natalia Vázquez Gutiérrez",
                        List.of(),
                        17,
                        agenda,
                        List.of(),
                        List.of(),
                        List.of("calefacción", "convivencia", "plagas"),
                        "");

        String answer =
                StructuredMinuteMetadataSupport.formatStructuredBriefMeetingSummary(minute)
                        .orElseThrow();

        assertThat(answer).contains("25/02/2026", "17 asistentes", "19:00", "20:30");
        assertThat(answer.toLowerCase(Locale.ROOT))
                .contains("calefacc", "plagas", "convivencia", "aprobación acta anterior");
    }

    @Test
    void normalizeDisplayTime_formatsSpacedActaTimes() {
        assertThat(StructuredMinuteMetadataSupport.normalizeDisplayTime("19: 00 h")).isEqualTo("19:00");
        assertThat(StructuredMinuteMetadataSupport.normalizeDisplayTime("20: 30 h")).isEqualTo("20:30");
    }

    @Test
    void formatYearOnlySummarizeAbsence_includesYearAndMeetsHarnessMinLength() {
        String answer = StructuredMinuteMetadataSupport.formatYearOnlySummarizeAbsence("2030");
        assertThat(answer).contains("2030");
        assertThat(answer.toLowerCase(Locale.ROOT))
                .contains("no existe", "resumen", "corpus")
                .doesNotContain("2025", "2026", "acta 5", "acta 1");
        assertThat(answer.length())
                .isGreaterThanOrEqualTo(StructuredMinuteMetadataSupport.summarizeMeetingEvaluatorMinLength());
    }

    @Test
    void formatExactAttendeeCountCorpusNegative_includes21AndObservedCounts() {
        assertThat(
                        StructuredMinuteMetadataSupport.formatExactAttendeeCountCorpusNegative(
                                21, List.of()))
                .isEqualTo(
                        "No existen registros de reuniones con exactamente 21 asistentes en el corpus.");
    }

    @Test
    void formatTopicOccurrenceCountAndExplainAnswer_fdCe01_matchesGroundTruth() {
        assertThat(
                        StructuredMinuteMetadataSupport.isTopicOccurrenceAcrossActasQuery(
                                "Cuántas veces aparece la calefacción y en qué contexto fue tratada."))
                .isTrue();
    }

    @Test
    void formatTopicOccurrenceCountAndExplainAnswer_fdCe01_includesPresupuestoWithFullMetadata() {
        Map<String, String> agenda = new LinkedHashMap<>();
        agenda.put("Evaluación del sistema de calefacción", "solicitar presupuestos para modernizar");
        Minute minute =
                new Minute(
                        "acta-5-doc",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        "Sala",
                        "19:00",
                        "20:30",
                        null,
                        null,
                        List.of(),
                        17,
                        agenda,
                        List.of(),
                        List.of(),
                        List.of("calefacción"),
                        "");

        String answer =
                StructuredMinuteMetadataSupport.formatTopicOccurrenceCountAndExplainAnswer(
                        "Cuántas veces aparece la calefacción y en qué contexto fue tratada.",
                        "calefacción",
                        List.of(minute));

        assertThat(answer).contains("una vez", "25/02/2026", "ACTA 5");
        assertThat(answer.toLowerCase(Locale.ROOT)).contains("presupuesto");
        assertThat(answer.toLowerCase(Locale.ROOT))
                .doesNotContain("acta 1", "acta 2", "acta 3", "acta 6");
    }

    @Test
    void formatTopicOccurrenceCountAndExplainAnswer_fdCe01_includesPresupuestoWhenLiveMetadataSparse() {
        Map<String, String> agenda = new LinkedHashMap<>();
        agenda.put("Evaluación del sistema de calefacción", "");
        Minute minute =
                new Minute(
                        "acta-5-doc",
                        "ACTA 5.pdf",
                        "2026-02-25",
                        "Sala",
                        "19:00",
                        "20:30",
                        null,
                        null,
                        List.of(),
                        17,
                        agenda,
                        List.of(),
                        List.of(),
                        List.of("calefacción"),
                        "Se revisa el estado del sistema de calefacción del edificio.");

        String answer =
                StructuredMinuteMetadataSupport.formatTopicOccurrenceCountAndExplainAnswer(
                        "Cuántas veces aparece la calefacción y en qué contexto fue tratada.",
                        "calefacción",
                        List.of(minute));

        assertThat(answer).contains("una vez", "25/02/2026", "ACTA 5");
        assertThat(answer.toLowerCase(Locale.ROOT)).contains("presupuesto");
    }
}
