package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateGroundingSupportTest {

    @Test
    void exactDateQuestionWithExistingActaMatchesOnlyRequestedDate() {
        RetrievalCandidate acta2026 = candidate("ACTA5.pdf", "Presidente: Ana. Secretaria: Beatriz.", Map.of("date_iso", "2026-02-25"));
        RetrievalCandidate acta2025 = candidate("ACTA2.pdf", "Presidente: Carlos.", Map.of("date_iso", "2025-02-25"));

        var requested = DateGroundingSupport.requestedDate("¿Quién fue el presidente del acta del 25/02/2026?").orElseThrow();
        List<RetrievalCandidate> selected = DateGroundingSupport.preferExactDate(List.of(acta2025, acta2026), requested);
        var decision = DateGroundingSupport.decision("¿Quién fue el presidente del acta del 25/02/2026?", selected);

        assertThat(selected).containsExactly(acta2026);
        assertThat(decision.exactDateMatch()).isTrue();
        assertThat(decision.matchedDocumentDates()).containsExactly("2026-02-25");
    }

    @Test
    void nonexistentExactDateAbstainsWithAvailableNearbyActasOnlyAsAlternatives() {
        RetrievalCandidate acta2026 = candidate("ACTA5.pdf", "Fecha: 25 de febrero de 2026", Map.of());
        RetrievalCandidate acta2025 = candidate("ACTA2.pdf", "Fecha: 25 de febrero de 2025", Map.of());

        var selected = DateGroundingSupport.preferExactDate(
                List.of(acta2025, acta2026),
                DateGroundingSupport.requestedDate("acta del 25/02/2027").orElseThrow());
        var decision = DateGroundingSupport.decision("acta del 25/02/2027", selected);
        String answer = DateGroundingSupport.mismatchMessage("acta del 25/02/2027", decision);

        assertThat(decision.exactDateMatch()).isFalse();
        assertThat(decision.dateMismatchDetected()).isTrue();
        assertThat(decision.abstentionReason()).isEqualTo("no_exact_date_source");
        assertThat(answer).contains("No he encontrado un acta con fecha 2027-02-25");
        assertThat(answer).contains("ACTA5.pdf (2026-02-25)");
    }

    @Test
    void similarActaFromPreviousYearDoesNotSatisfyRequestedYear() {
        RetrievalCandidate acta2025 = candidate("ACTA2.pdf", "Fecha: 25 de febrero de 2025", Map.of());

        var decision = DateGroundingSupport.decision("Dame el acta del 25/02/2026", List.of(acta2025));

        assertThat(decision.exactDateMatch()).isFalse();
        assertThat(decision.dateMismatchDetected()).isTrue();
        assertThat(DateGroundingSupport.mismatchMessage("Dame el acta del 25/02/2026", decision))
                .contains("2026-02-25")
                .contains("2025-02-25");
    }

    @Test
    void filenameDateTakesPrecedenceOverUnrelatedContentDates() {
        RetrievalCandidate acta2026 =
                candidate("acta-25-02-2026.pdf", "Se compara con la sesión del 25/02/2025.", Map.of());

        var requested = DateGroundingSupport.requestedDate("Dame el acta del 25/02/2026").orElseThrow();

        assertThat(DateGroundingSupport.profile(acta2026).isoDate()).isEqualTo("2026-02-25");
        assertThat(DateGroundingSupport.candidateMatchesRequestedDate(acta2026, requested)).isTrue();
    }

    @Test
    void presidentOfConcreteActaRequiresExactDateEvidence() {
        RetrievalCandidate acta2026 = candidate(
                "ACTA5.pdf",
                "Fecha: 25 de febrero de 2026. Presidente: Ana Garcia.",
                Map.of("date_iso", "2026-02-25", "president", "Ana Garcia"));

        var requested = DateGroundingSupport.requestedDate("presidente del acta del 25/02/2026").orElseThrow();

        assertThat(DateGroundingSupport.candidateMatchesRequestedDate(acta2026, requested)).isTrue();
        assertThat(acta2026.metadata()).containsEntry("president", "Ana Garcia");
    }

    @Test
    void secretaryOfConcreteActaIsUnsupportedWhenSourceDoesNotContainSecretary() {
        RetrievalCandidate acta2026 = candidate(
                "ACTA5.pdf",
                "Fecha: 25 de febrero de 2026. Presidente: Ana Garcia.",
                Map.of("date_iso", "2026-02-25", "president", "Ana Garcia"));

        assertThat(acta2026.metadata()).doesNotContainKey("secretary");
        var decision = DateGroundingSupport.decision("secretaria del acta del 25/02/2026", List.of(acta2026));
        assertThat(decision.exactDateMatch()).isTrue();
    }

    @Test
    void questionWithoutDateDoesNotApplyDateMismatchPolicy() {
        RetrievalCandidate acta2026 = candidate("ACTA5.pdf", "Sin informacion sobre presupuesto extraordinario", Map.of("date_iso", "2026-02-25"));

        var decision = DateGroundingSupport.decision("¿Qué dice sobre presupuesto extraordinario?", List.of(acta2026));

        assertThat(decision.requestedDate()).isNull();
        assertThat(decision.dateMismatchDetected()).isFalse();
    }

    @Test
    void regression_question25Feb2026_mustNotTreatActa2025AsExactMatch() {
        RetrievalCandidate acta2025 = candidate("ACTA2.pdf", "Presidente: Carlos.", Map.of("date_iso", "2025-02-25"));
        RetrievalCandidate acta2026 = candidate("ACTA5.pdf", "Presidente: Ana.", Map.of("date_iso", "2026-02-25"));

        var requested = DateGroundingSupport.requestedDate("¿Presidente del acta del 25/02/2026?").orElseThrow();
        var decision =
                DateGroundingSupport.decision("¿Presidente del acta del 25/02/2026?", List.of(acta2025, acta2026));

        assertThat(DateGroundingSupport.candidatesForGroundedAnswer(decision, List.of(acta2025, acta2026)))
                .containsExactly(acta2026);
        assertThat(DateGroundingSupport.candidateMatchesRequestedDate(acta2025, requested)).isFalse();
        assertThat(DateGroundingSupport.candidateMatchesRequestedDate(acta2026, requested)).isTrue();
    }

    @Test
    void candidatesForGroundedAnswer_emptyOnMismatch() {
        RetrievalCandidate acta2025 = candidate("ACTA2.pdf", "Fecha 25/02/2025", Map.of("date_iso", "2025-02-25"));
        var decision = DateGroundingSupport.decision("acta del 25/02/2026", List.of(acta2025));

        assertThat(decision.dateMismatchDetected()).isTrue();
        assertThat(DateGroundingSupport.candidatesForGroundedAnswer(decision, List.of(acta2025))).isEmpty();
    }

    @Test
    void T_M5_BE_dateAbstain_futureDateWithOnlyWrongYearSources_mismatchWithoutExactMatch() {
        RetrievalCandidate acta2025 = candidate("acta-24-02-2025.txt", "Presidente: Juan Pérez García.", Map.of("date_iso", "2025-02-24"));

        var decision =
                DateGroundingSupport.decision("¿Quién presidió el acta del 15 de marzo de 2099?", List.of(acta2025));
        String answer = DateGroundingSupport.mismatchMessage("¿Quién presidió el acta del 15 de marzo de 2099?", decision);

        assertThat(decision.exactDateMatch()).isFalse();
        assertThat(decision.dateMismatchDetected()).isTrue();
        assertThat(decision.abstentionReason()).isEqualTo("no_exact_date_source");
        assertThat(answer).contains("2099-03-15");
        assertThat(answer).doesNotContain("Juan");
    }

    @Test
    void unsupportedSourcesTriggerAbstentionReasonForExactDateQuestion() {
        var decision = DateGroundingSupport.decision("¿Qué dice el acta del 25/02/2026 sobre X?", List.of());

        assertThat(decision.exactDateMatch()).isFalse();
        assertThat(decision.abstentionReason()).isEqualTo("no_source_candidates");
        assertThat(DateGroundingSupport.mismatchMessage("¿Qué dice el acta del 25/02/2026 sobre X?", decision))
                .contains("No he encontrado un acta con fecha 2026-02-25");
    }

    private static RetrievalCandidate candidate(String filename, String content, Map<String, Object> metadata) {
        Map<String, Object> meta = new LinkedHashMap<>(metadata);
        meta.put("filename", filename);
        return new RetrievalCandidate(
                filename,
                content,
                meta,
                0.0,
                0.0,
                1,
                0,
                UUID.randomUUID(),
                1.0);
    }
}
