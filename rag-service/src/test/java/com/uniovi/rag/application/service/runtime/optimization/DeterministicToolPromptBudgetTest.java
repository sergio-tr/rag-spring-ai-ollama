package com.uniovi.rag.application.service.runtime.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceHolder;
import com.uniovi.rag.domain.model.Minute;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeterministicToolPromptBudgetTest {

    @AfterEach
    void tearDown() {
        DeterministicToolEvidenceHolder.clear();
    }

    @Test
    void primaryAnswerBudget_capsAtTwentyThousand() {
        String huge = "x".repeat(30_000);
        var budget = DeterministicToolPromptBudgetPolicy.budgetPrimaryAnswerContext(huge);
        assertThat(budget.finalChars()).isLessThanOrEqualTo(20_000);
        assertThat(budget.truncated()).isTrue();
    }

    @Test
    void shouldUseToolScopedContext_whenHighConfidenceEvidence() {
        DeterministicToolEvidenceHolder.set(
                new DeterministicToolEvidenceHolder.Evidence(
                        List.of(
                                new Minute(
                                        "m1",
                                        "ACTA 1.pdf",
                                        "2026-02-25",
                                        "Sala",
                                        "",
                                        "",
                                        "",
                                        "",
                                        List.of(),
                                        0,
                                        Map.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        "")),
                        "agenda: item",
                        true));
        QueryPlan plan = plan(QueryType.FILTER_AND_LIST, "dime qué actas tienen 20 asistentes");
        assertThat(DeterministicToolPromptBudgetPolicy.shouldUseToolScopedContext(plan)).isTrue();
    }

    @Test
    void chunkDenseMetadataWorkflow_usesToolScopedContextWithinBudget() {
        String scoped = "videovigilancia: acta 1\n".repeat(500);
        DeterministicToolEvidenceHolder.set(
                new DeterministicToolEvidenceHolder.Evidence(List.of(), scoped, true));
        QueryPlan plan = plan(QueryType.FIND_PARAGRAPH, "dime en cuántas reuniones se trató videovigilancia");
        assertThat(
                        DeterministicToolPromptBudgetPolicy.shouldUseToolScopedContext(
                                plan, "ChunkDenseMetadataWorkflow"))
                .isTrue();
        assertThat(
                        DeterministicToolPromptBudgetPolicy.qualifiesForToolDirectAnswer(
                                plan, "2 reuniones trataron videovigilancia.", "ChunkDenseMetadataWorkflow"))
                .isTrue();
    }

    private static QueryPlan plan(QueryType type, String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                "lbl",
                Optional.of(type),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "cid",
                "",
                List.of());
    }
}
