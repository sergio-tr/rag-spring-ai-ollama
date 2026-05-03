package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AmbiguityAssessmentServiceTest {

    @Test
    void canInvokeAssessmentThroughInterfaceType() {
        AmbiguityAssessmentService svc = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                svc.assess(
                        new NormalizedQuery("raw", "summarize last meeting", List.of()),
                        Optional.of(QueryType.SUMMARIZE_MEETING),
                        "label",
                        ClassifierStatus.OK,
                        new StructuredRewriteResult(
                                "summarize last meeting",
                                false,
                                List.of(),
                                StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Map.of(),
                                List.of()),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out).isNotNull();
    }
}

