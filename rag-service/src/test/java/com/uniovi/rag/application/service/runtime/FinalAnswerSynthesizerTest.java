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
import com.uniovi.rag.domain.runtime.engine.AnswerFinality;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FinalAnswerSynthesizerTest {

  @Test
  void stripsInternalRouteLabels() {
    QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);
    String raw =
        "PARENT_P3 baseline_floor_kept_parent: native_not_constraint_complete Se encontraron 2 actas.";

    String out = FinalAnswerSynthesizer.synthesize(plan, raw, List.of());

    assertThat(out).isEqualTo("Se encontraron 2 actas.");
    assertThat(out).doesNotContain("PARENT_P3").doesNotContain("baseline_floor");
  }

  @Test
  void fdCd03_negativeYearCount_notCorruptedByDigitPrepend() {
    QueryPlan plan =
        plan("Número de actas registradas en el año 2028.", QueryType.COUNT_DOCUMENTS);
    String raw = "No existen actas correspondientes al año 2028 en el corpus.";

    String out = FinalAnswerSynthesizer.synthesize(plan, raw, List.of());

    assertThat(out).isEqualTo(raw);
    assertThat(out).doesNotContain("Se encontraron 2028 actas");
  }

  @Test
  void formatsBareCountInSpanish() {
    QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);

    String out = FinalAnswerSynthesizer.synthesize(plan, "2", List.of());

    assertThat(out)
        .isEqualTo(
            "En total son 2 actas. Indica el criterio si necesitas el detalle de cada una.");
  }

  @Test
  void formatsCountWithSupportingActaList() {
    QueryPlan plan =
        plan(
            "¿Qué actas mencionan videovigilancia?",
            QueryType.FILTER_AND_LIST);
    String raw =
        "Dos actas mencionan videovigilancia: ACTA_1.pdf y ACTA_6.pdf.";

    String out = FinalAnswerSynthesizer.synthesize(plan, raw, List.of());

    assertThat(out).contains("ACTA_1.pdf").contains("ACTA_6.pdf");
    assertThat(out).contains("- ACTA_1.pdf");
    assertThat(out).contains("- ACTA_6.pdf");
  }

  @Test
  void normalizesTerseUnavailableToSpanishExplanation() {
    QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);

    String out = FinalAnswerSynthesizer.synthesize(plan, "No consta.", List.of());

    assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
  }

  @Test
  void enrichesTimingOnlySummaryWithLimitationNote() {
    QueryPlan plan = plan("Resume el acta del 25/02/2026.", QueryType.SUMMARIZE_MEETING);
    String raw =
        "La reunión duró de 19:00 a 20:30 y asistieron 17 propietarios.";

    String out = FinalAnswerSynthesizer.synthesize(plan, raw, List.of());

    assertThat(out).contains("19:00");
    assertThat(out).contains("fragmentos disponibles");
    assertThat(out).doesNotContain("PARENT");
  }

  @Test
  void appendsSourceReferencesWhenMissing() {
    QueryPlan plan = plan("¿Quién presidió la reunión?", QueryType.GET_FIELD);
    List<Map<String, Object>> sources =
        List.of(Map.of("filename", "ACTA_2026-02-25.pdf", "documentId", "d1"));

    String out =
        FinalAnswerSynthesizer.synthesize(
                plan, "El presidente fue Jorge Moreno Navarro.", sources);

    assertThat(out).contains("Fuentes consultadas: ACTA_2026-02-25.pdf");
  }

  @Test
  void safeTerminalFormatting_preservesCountDatesAndActaReferences() {
    QueryPlan plan =
        plan("Número de actas registradas en el año 2028.", QueryType.COUNT_DOCUMENTS);
    String raw = "No existen actas correspondientes al año 2028 en el corpus.";
    RagExecutionResult terminal =
        new RagExecutionResult(
            raw,
            "deterministic-tool",
            false,
            true,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            ExecutionTrace.placeholder(),
            "deterministic-tool",
            QueryType.COUNT_DOCUMENTS,
            true,
            List.of(),
            Optional.empty(),
            List.of(),
            AnswerFinality.DETERMINISTIC_TOOL_FINAL);

    RagExecutionResult out = FinalAnswerSynthesizer.apply(plan, terminal);

    assertThat(out.answerText()).isEqualTo(raw);
    assertThat(out.answerFinality()).isEqualTo(AnswerFinality.DETERMINISTIC_TOOL_FINAL);
    assertThat(out.allowPostSynthesisRewrite()).isFalse();
  }

  @Test
  void safeTerminalFormatting_stripsInternalLabelsWithoutReshapingCount() {
    QueryPlan plan = plan("¿Cuántas actas mencionan el ascensor?", QueryType.COUNT_DOCUMENTS);
    String raw =
        "DETERMINISTIC_TOOL_ROUTE Se encontraron 2 actas: ACTA_1.pdf y ACTA_6.pdf.";
    RagExecutionResult terminal =
        new RagExecutionResult(
            raw,
            "deterministic-tool",
            false,
            true,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            ExecutionTrace.placeholder(),
            "deterministic-tool",
            QueryType.COUNT_DOCUMENTS,
            true,
            List.of(),
            Optional.empty(),
            List.of(),
            AnswerFinality.DETERMINISTIC_TOOL_FINAL);

    RagExecutionResult out = FinalAnswerSynthesizer.apply(plan, terminal);

    assertThat(out.answerText())
        .isEqualTo("Se encontraron 2 actas: ACTA_1.pdf y ACTA_6.pdf.");
    assertThat(out.answerText()).contains("ACTA_1.pdf").contains("ACTA_6.pdf");
    assertThat(out.answerText()).doesNotContain("DETERMINISTIC_TOOL_ROUTE");
  }

  @Test
  void safeTerminalFormatting_preservesGetDurationTokensWithoutSourceAppend() {
    QueryPlan plan =
        plan("Duración de la reunión del 25 de febrero de 2026.", QueryType.GET_DURATION);
    String raw =
        "La reunión del 25 de febrero de 2026 comenzó a las 19:00 y terminó a las 20:30 (1 hora y 30 minutos / 90 minutos).";
    RagExecutionResult terminal =
        new RagExecutionResult(
            raw,
            "deterministic-tool",
            false,
            true,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            ExecutionTrace.placeholder(),
            "deterministic-tool",
            QueryType.GET_DURATION,
            true,
            List.of(),
            Optional.empty(),
            List.of(Map.of("filename", "ACTA 5.pdf")),
            AnswerFinality.DETERMINISTIC_TOOL_FINAL);

    RagExecutionResult out = FinalAnswerSynthesizer.apply(plan, terminal);

    assertThat(out.answerText()).isEqualTo(raw);
    assertThat(out.answerText()).contains("19:00", "20:30", "90");
    assertThat(out.answerText()).doesNotContain("Fuentes consultadas");
    assertThat(out.answerFinality()).isEqualTo(AnswerFinality.DETERMINISTIC_TOOL_FINAL);
  }

  @Test
  void preservesStructuredToolAnswerWithEvidence() {
    QueryPlan plan = plan("¿Quiénes asistieron?", QueryType.GET_FIELD);
    String raw =
        "En el acta del 25 de febrero de 2026 (ACTA_2026-02-25.pdf), los participantes fueron: Ana Sánchez Herrera, Pedro Jiménez López (2 en total).";

    String out = FinalAnswerSynthesizer.synthesize(plan, raw, List.of());

    assertThat(out).isEqualTo(raw);
    assertThat(out).contains("participantes fueron");
    assertThat(out).contains("ACTA_2026-02-25.pdf");
  }

    @Test
    void enumerateAllWhenToolReturnsN() {
        QueryPlan plan =
                plan("¿Qué actas mencionan videovigilancia?", QueryType.FILTER_AND_LIST);
        List<Map<String, Object>> sources =
                List.of(
                        Map.of("filename", "ACTA_1.pdf"),
                        Map.of("filename", "ACTA_2.pdf"),
                        Map.of("filename", "ACTA_3.pdf"));
        String raw = "Una acta menciona videovigilancia.";

        String out = FinalAnswerSynthesizer.synthesize(plan, raw, sources);

        assertThat(out).contains("ACTA_1.pdf", "ACTA_2.pdf", "ACTA_3.pdf");
        assertThat(out).contains("- ACTA_1.pdf");
    }

    private static QueryPlan plan(String query, QueryType queryType) {
    return new QueryPlan(
        QueryPlan.VERSION_P6_QU_CORE_V1,
        query,
        query,
        query,
        query,
        queryType.name(),
        Optional.of(queryType),
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
