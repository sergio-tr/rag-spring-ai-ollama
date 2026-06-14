package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.metrics.GoldSubsetManifest;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeterministicToolEvidenceEvaluatorTest {

    @Test
    void weakLivePlans_selectSingleStrongKindForApplicableGoldSubset() throws Exception {
        GoldSubsetManifest manifest =
                new ObjectMapper()
                        .readValue(
                                getClass().getResourceAsStream("/evaluation/gold-subset-v1.json"),
                                GoldSubsetManifest.class);
        int applicable = 0;
        int strongSingle = 0;
        for (GoldSubsetManifest.Entry entry : manifest.entries()) {
            QueryType expected = QueryType.valueOf(entry.queryTypeExpected().trim());
            if (!DeterministicToolApplicability.isApplicableQueryType(expected)) {
                continue;
            }
            applicable++;
            QueryPlan plan = weakLivePlan(entry.question());
            var evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
            if (evaluation.evidenceLevel() == DeterministicEvidenceLevel.STRONG
                    && evaluation.matchedKinds().size() == 1) {
                strongSingle++;
            }
        }
        assertThat(applicable).isGreaterThanOrEqualTo(12);
        assertThat(strongSingle * 100.0 / applicable).isGreaterThanOrEqualTo(80.0);
    }

    @Test
    void meetingMentionCount_prefersCountDocumentsOverFindParagraph() {
        QueryPlan plan =
                weakLivePlan("¿Se habló de la radiación solar en alguna reunión?");
        var evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        assertThat(evaluation.evidenceLevel()).isEqualTo(DeterministicEvidenceLevel.STRONG);
        assertThat(evaluation.singleKind()).contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
    }

    @Test
    void verifySi_prefersBooleanOverFindParagraph() {
        QueryPlan plan =
                weakLivePlan("Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.");
        var evaluation = DeterministicToolEvidenceEvaluator.evaluate(plan);
        assertThat(evaluation.evidenceLevel()).isEqualTo(DeterministicEvidenceLevel.STRONG);
        assertThat(evaluation.singleKind()).contains(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
    }

    private static QueryPlan weakLivePlan(String question) {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                question,
                question,
                question,
                question,
                "UNCLASSIFIED",
                Optional.empty(),
                ClassifierStatus.LOW_CONFIDENCE,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(question, "simulated-live"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "cid",
                "",
                List.of());
    }
}
