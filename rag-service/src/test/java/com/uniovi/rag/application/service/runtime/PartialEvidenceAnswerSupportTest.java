package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PartialEvidenceAnswerSupportTest {

    @Test
    void enrichIfPartial_returnsNullWhenAnswerNull() {
        assertThat(PartialEvidenceAnswerSupport.enrichIfPartial(null, null, List.of(Map.of("id", "1"))))
                .isNull();
    }

    @Test
    void enrichIfPartial_skipsWhenLimitationAlreadyStated() {
        String answer = "Limitación: no consta el detalle completo.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        planWithQuery("¿Quiénes asistieron?"), answer, List.of(Map.of("id", "1")));
        assertThat(out).isEqualTo(answer);
    }

    @Test
    void enrichIfPartial_appendsSpanishAttendeeNameGap() {
        QueryPlan plan = planWithQuery("¿Quiénes asistieron a la reunión?");
        String answer = "Había 3 asistentes en total.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        plan, answer, List.of(Map.of("chunkId", "c1")));
        assertThat(out).contains("Limitación:");
        assertThat(out).contains("lista completa de nombres");
    }

    @Test
    void enrichIfPartial_appendsEnglishAttendeeNameGap() {
        QueryPlan plan = planWithQuery("How many participants attended the session?");
        String answer = "There were 2 participants.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        plan, answer, List.of(Map.of("chunkId", "c1")));
        assertThat(out).contains("Limitation:");
        assertThat(out).contains("full name list");
    }

    @Test
    void enrichIfPartial_appendsSpanishActaPartialEvidenceNote() {
        QueryPlan plan = planWithQuery("Resume el acta de la reunión");
        String answer = "Se acordó el presupuesto.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        plan, answer, List.of(Map.of("chunkId", "c1")));
        assertThat(out).contains("evidencia recuperada es parcial");
    }

    @Test
    void enrichIfPartial_skipsAttendeeGapWhenNamesPresent() {
        QueryPlan plan = planWithQuery("¿Quiénes asistieron a la reunión?");
        String answer = "Asistieron Jorge García y 2 participantes más.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        plan, answer, List.of(Map.of("chunkId", "c1")));
        assertThat(out).isEqualTo(answer);
    }

    @Test
    void enrichIfPartial_returnsUnchangedWhenNoPartialSignal() {
        QueryPlan plan = planWithQuery("What is RAG?");
        String answer =
                "Retrieval-Augmented Generation combines retrieval with generation for grounded answers.";
        String out =
                PartialEvidenceAnswerSupport.enrichIfPartial(
                        plan, answer, List.of(Map.of("chunkId", "c1")));
        assertThat(out).isEqualTo(answer);
    }

    private static QueryPlan planWithQuery(String query) {
        QueryPlan plan = mock(QueryPlan.class);
        when(plan.rewrittenQueryText()).thenReturn(query);
        when(plan.normalizedQueryText()).thenReturn(query);
        return plan;
    }
}
