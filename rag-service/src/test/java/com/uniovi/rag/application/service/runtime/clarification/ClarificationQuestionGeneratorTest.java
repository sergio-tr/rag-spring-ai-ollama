package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestion;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
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

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationQuestionGeneratorTest {

    private final ClarificationQuestionGenerator generator = new ClarificationQuestionGenerator();

    @Test
    void questionForKind_usesFrozenTemplate_andMissingFieldsFromPlan() {
        QueryPlan plan = minimalPlan(List.of("topic"));
        ClarificationQuestion q =
                generator.questionForKind(ClarificationQuestionKind.MISSING_TOPIC, plan);
        assertThat(q.questionText()).isEqualTo(ClarificationQuestionKind.MISSING_TOPIC.templateText());
        assertThat(q.questionKind()).isEqualTo(ClarificationQuestionKind.MISSING_TOPIC);
        assertThat(q.requestedFields()).containsExactly("topic");
    }

    private static QueryPlan minimalPlan(List<String> missingFields) {
        return new QueryPlan(
                QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, List.of(), missingFields),
                "c",
                "",
                List.of());
    }
}
