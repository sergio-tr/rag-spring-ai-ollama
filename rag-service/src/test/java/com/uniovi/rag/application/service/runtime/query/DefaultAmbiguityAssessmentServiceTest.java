package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAmbiguityAssessmentServiceTest {

    private static StructuredRewriteResult rewriteWithAction(String action) {
        return new StructuredRewriteResult(
                "q",
                true,
                List.of(),
                StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                List.of(),
                List.of(),
                Optional.of(action),
                Map.of(),
                List.of());
    }

    @Test
    void assess_treatsExtractFieldAsCompatibleWithGetFieldClassifier() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "dime los participantes del acta del 25 de febrero de 2026", List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        "GET_FIELD",
                        ClassifierStatus.OK,
                        rewriteWithAction("EXTRACT_FIELD"),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_treatsListAsCompatibleWithGetFieldClassifier() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "dime los participantes del acta del 25 de febrero de 2026", List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        "GET_FIELD",
                        ClassifierStatus.OK,
                        rewriteWithAction("LIST"),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_detectsConflict_betweenClassifierAndRewriteAction() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "whatever", List.of()),
                        Optional.of(QueryType.COMPARE),
                        "label",
                        ClassifierStatus.OK,
                        rewriteWithAction("SUMMARIZE_TOPIC"),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.CONFLICTING_CUES);
        assertThat(out.reasons()).anyMatch(s -> s.contains("CONFLICT"));
        assertThat(out.missingFields()).isEmpty();
    }

    @Test
    void assess_detectsMissingTemporalAnchor_forSummaryOrCompare() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "summarize meeting", List.of()),
                        Optional.empty(),
                        "label",
                        ClassifierStatus.DISABLED,
                        StructuredRewriteResult.identityFallback("summarize meeting", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
    }

    @Test
    void assess_detectsMissingDate_forAmbiguousPresident() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "¿Quién fue el presidente?", List.of()),
                        Optional.empty(),
                        "UNCLASSIFIED",
                        ClassifierStatus.LOW_CONFIDENCE,
                        StructuredRewriteResult.identityFallback("¿Quién fue el presidente?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
    }

    @Test
    void assess_detectsMissingDate_forAmbiguousPresident_evenWithGeneralTemporalContext() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.of("general"),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "¿Quién fue el presidente?", List.of()),
                        Optional.empty(),
                        "UNCLASSIFIED",
                        ClassifierStatus.LOW_CONFIDENCE,
                        StructuredRewriteResult.identityFallback("¿Quién fue el presidente?", null),
                        entities);

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
    }

    @Test
    void assess_returnsSufficient_forSummaryWithExplicitYear() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "Resume el acta del año 2030.", List.of()),
                        Optional.empty(),
                        "label",
                        ClassifierStatus.DISABLED,
                        StructuredRewriteResult.identityFallback("Resume el acta del año 2030.", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_returnsSufficient_whenNoConflictAndHasTemporalAnchor() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of("2026-01-01"),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "summarize meeting", List.of()),
                        Optional.empty(),
                        "label",
                        ClassifierStatus.DISABLED,
                        StructuredRewriteResult.identityFallback("summarize meeting", null),
                        entities);

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }
}

