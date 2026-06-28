package com.uniovi.rag.application.service.runtime.functioncalling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FunctionCallSchemaValidatorTest {

    @Test
    void acceptsCanonicalArguments() {
        QueryPlan plan = countPlan("how many documents mention budget?");
        String args = FunctionCallArgumentBuilder.buildJson(DeterministicToolKind.COUNT_DOCUMENTS_TOOL, plan);
        assertThat(FunctionCallSchemaValidator.isValid(args, DeterministicToolKind.COUNT_DOCUMENTS_TOOL, plan))
                .isTrue();
    }

    @Test
    void rejectsMismatchedQuery() {
        QueryPlan plan = countPlan("how many documents mention budget?");
        assertThat(FunctionCallSchemaValidator.isValid("{\"query\":\"other\"}", DeterministicToolKind.COUNT_DOCUMENTS_TOOL, plan))
                .isFalse();
    }

    @Test
    void repairFixesQueryMismatchOnce() {
        QueryPlan plan = countPlan("how many documents mention budget?");
        FunctionCallSchemaValidator.ValidationWithRepairResult result =
                FunctionCallSchemaValidator.validateWithOptionalRepair(
                        "{\"query\":\"wrong\"}", DeterministicToolKind.COUNT_DOCUMENTS_TOOL, plan);
        assertThat(result.repairAttempted()).isTrue();
        assertThat(result.repairSucceeded()).isTrue();
        assertThat(result.valid()).isTrue();
    }

    @Test
    void repairDoesNotFixMissingFieldForGetField() {
        QueryPlan plan = fieldPlan("what is the date?", "fecha");
        FunctionCallSchemaValidator.ValidationWithRepairResult result =
                FunctionCallSchemaValidator.validateWithOptionalRepair(
                        "{\"query\":\"wrong\"}", DeterministicToolKind.GET_FIELD_TOOL, plan);
        assertThat(result.repairAttempted()).isTrue();
        assertThat(result.valid()).isTrue();
        assertThat(result.argumentsJson()).contains("fecha");
    }

    @Test
    void validateOrThrowFailsOnEmptyArguments() {
        QueryPlan plan = countPlan("q");
        assertThatThrownBy(() -> FunctionCallSchemaValidator.validateOrThrow("", DeterministicToolKind.COUNT_DOCUMENTS_TOOL, plan))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static QueryPlan countPlan(String query) {
        return basePlan(query, List.of(), Map.of());
    }

    private static QueryPlan fieldPlan(String query, String field) {
        return basePlan(query, List.of(field), Map.of("field", field));
    }

    private static QueryPlan basePlan(String query, List<String> attrs, Map<String, String> slots) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                query,
                query,
                query,
                query,
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                slots,
                List.of(),
                attrs,
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, "test"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "cid",
                "",
                List.of());
    }
}
