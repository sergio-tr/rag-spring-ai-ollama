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
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FinalAnswerStubSanitizerTest {

    @Test
    void finalAnswerRejectsFoundRelevantMinutesOnly() {
        QueryPlan plan = plan("en cuántas actas aparece Rosa Aguilar Fernández", QueryType.COUNT_DOCUMENTS);

        String out =
                FinalAnswerSynthesizer.synthesize(
                        plan, "Found 2 relevant meeting minutes.", List.of());

        assertThat(out).doesNotContain("Found 2 relevant meeting minutes");
        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void finalAnswerRejectsMoreInformationOnly() {
        QueryPlan plan = plan("tell me about the elevator", QueryType.FIND_PARAGRAPH);

        String out = FinalAnswerSynthesizer.synthesize(plan, "More information", List.of());

        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN);
        assertThat(out).doesNotContain("More information");
    }

    @Test
    void finalAnswerRejectsTechnicalClarificationOnly() {
        QueryPlan plan = plan("¿Quién fue la secretaria?", QueryType.GET_FIELD);
        String leakedClarification = ClarificationQuestionKind.MISSING_DATE.templateText();

        String out = FinalAnswerSynthesizer.synthesize(plan, leakedClarification, List.of());

        assertThat(out).doesNotContain("¿A qué acta o reunión te refieres?");
        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void finalAnswerComposesNaturalCountAnswerFromStructuredToolResult() {
        QueryPlan plan = plan("en cuántas actas aparece Rosa Aguilar Fernández", QueryType.COUNT_DOCUMENTS);
        var context = FinalAnswerStubSanitizer.StructuredToolContext.ofCount(2);

        String out =
                FinalAnswerStubSanitizer.sanitizeForUser(
                        plan,
                        "Found 2 relevant meeting minutes.",
                        List.of(),
                        context);

        assertThat(out).isEqualTo("Rosa Aguilar Fernández aparece en 2 actas.");
        assertThat(out).doesNotContain("Found 2 relevant");
    }

    @Test
    void finalAnswerComposesDateListFromRelevantMinutes() {
        QueryPlan plan =
                plan(
                        "tell me the dates of the minutes where elevator issues are commented",
                        QueryType.FILTER_AND_LIST);
        List<Map<String, Object>> sources =
                List.of(
                        Map.of("detectedDate", "2025-01-15", "filename", "ACTA_1.pdf"),
                        Map.of("detectedDate", "2025-02-24", "filename", "ACTA_2.pdf"));

        String out =
                FinalAnswerStubSanitizer.sanitizeForUser(
                        plan, "Found 2 relevant meeting minutes:", sources);

        assertThat(out)
                .isEqualTo(
                        "The relevant meeting minutes correspond to these dates: January 15, 2025, February 24, 2025.");
        assertThat(out).doesNotContain("Found 2 relevant");
    }

    @Test
    void preservesSubstantiveAnswerContainingFoundPhrase() {
        QueryPlan plan = plan("¿Cuántas actas?", QueryType.COUNT_DOCUMENTS);
        String substantive =
                "Found 2 relevant meeting minutes: ACTA_1.pdf mentions the elevator and ACTA_2.pdf discusses budgets.";

        String out = FinalAnswerSynthesizer.synthesize(plan, substantive, List.of());

        assertThat(out).isEqualTo(substantive);
    }

    @Test
    void doesNotTreatPanelLabelInLongerAnswerAsStub() {
        assertThat(FinalAnswerStubSanitizer.isInternalStubOnly("More information")).isTrue();
        assertThat(
                        FinalAnswerStubSanitizer.isInternalStubOnly(
                                "The secretary was Ana López. See the panel for more information."))
                .isFalse();
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
