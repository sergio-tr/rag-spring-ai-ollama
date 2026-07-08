package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CorpusDateEvidenceAnswerGuardTest {

    @Test
    void detectsDenialWhenSourcesContainRequestedDate() {
        String query = "cuales son los asistentes del acta del 25 de agosto del 2025?";
        String answer = "No existe ninguna reunión registrada en esa fecha.";
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 3.pdf", "date_iso", "2025-08-25"));

        assertThat(CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, sources))
                .isTrue();
        assertThat(CorpusDateEvidenceAnswerGuard.groundedEvidenceReminder(query))
                .contains("2025-08-25")
                .contains("sí hay documentación");
    }

    @Test
    void doesNotTriggerWhenAnswerIsGrounded() {
        String query = "cuales son los asistentes del acta del 25 de agosto del 2025?";
        String answer = "Los asistentes fueron Ana García y Pedro López.";
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 3.pdf", "date_iso", "2025-08-25"));

        assertThat(CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, sources))
                .isFalse();
    }

    @Test
    void doesNotTriggerWhenSourcesLackRequestedDate() {
        String query = "cuales son los asistentes del acta del 25 de agosto del 2026?";
        String answer = "No he encontrado un acta con esa fecha.";
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 3.pdf", "date_iso", "2025-08-25"));

        assertThat(CorpusDateEvidenceAnswerGuard.answerDeniesDespiteMatchingSources(query, answer, sources))
                .isFalse();
    }

    @Test
    void finalAnswerSynthesizerPrependsReminderOnFalseDenial() {
        String query = "cuales son los asistentes del acta del 25 de febrero del 2025?";
        QueryPlan plan = plan(query);
        String answer = "No hay ninguna reunión en esa fecha.";
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 2.pdf", "date_iso", "2025-02-25"));

        String corrected = FinalAnswerSynthesizer.synthesizeSafeTerminal(plan, answer, sources);

        assertThat(corrected).startsWith("Según las fuentes recuperadas, sí hay documentación");
        assertThat(corrected).contains("2025-02-25");
        assertThat(corrected).contains(answer);
    }

    @Test
    void q4_futureDate_denialCorrectedWhenActa6InSources() {
        String query = "cuales son los asistentes del acta del 25 de agosto del 2026?";
        QueryPlan plan = plan(query);
        String answer = "No puede existir un acta con fecha futura en 2026.";
        List<Map<String, Object>> sources =
                List.of(Map.of("filename", "ACTA 6.pdf", "date_iso", "2026-08-25"));

        String corrected = FinalAnswerSynthesizer.synthesize(plan, answer, sources);

        assertThat(corrected).startsWith("Según las fuentes recuperadas, sí hay documentación");
        assertThat(corrected).contains("2026-08-25");
    }

    private static QueryPlan plan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.GET_FIELD.name(),
                Optional.of(QueryType.GET_FIELD),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
