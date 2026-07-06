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
    void assess_exactDatePresidentIsSufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "¿Quién fue el presidente en el acta del 25/02/2026?", List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        QueryType.GET_FIELD.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(
                                "¿Quién fue el presidente en el acta del 25/02/2026?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
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
    void assess_undatedParticipantCount_takesPriorityOverClassifierRewriteConflict() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "¿Cuántos participantes asistieron?", List.of()),
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        "COUNT_DOCUMENTS",
                        ClassifierStatus.OK,
                        rewriteWithAction("EXTRACT_FIELD"),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
        assertThat(out.reasons()).noneMatch(s -> s.contains("CONFLICT"));
    }

    @Test
    void assess_exactDateParticipantsIsSufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery(
                                "raw",
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                                List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        QueryType.GET_FIELD.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_ambiguousParticipantCount_requiresClarification() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "¿Cuántos participantes asistieron?", List.of()),
                        Optional.empty(),
                        "UNCLASSIFIED",
                        ClassifierStatus.LOW_CONFIDENCE,
                        StructuredRewriteResult.identityFallback("¿Cuántos participantes asistieron?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
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

    @Test
    void assess_fdFl03_compoundAugustVideovigilanciaFilter_isSufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();
        String q =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", q, List.of()),
                        Optional.of(QueryType.FILTER_AND_LIST),
                        QueryType.FILTER_AND_LIST.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(q, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
        assertThat(out.missingFields()).isEmpty();
    }

    @Test
    void assess_fdCe02_exactAttendeeListing_isSufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();
        String q =
                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", q, List.of()),
                        Optional.of(QueryType.COUNT_AND_EXPLAIN),
                        QueryType.COUNT_AND_EXPLAIN.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(q, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
        assertThat(out.missingFields()).isEmpty();
    }

    @Test
    void assess_broadScopeSummary_sufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "Resume todo lo tratado sobre calefacción.", List.of()),
                        Optional.of(QueryType.SUMMARIZE_TOPIC),
                        QueryType.SUMMARIZE_TOPIC.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(
                                "Resume todo lo tratado sobre calefacción.", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_corpusWideListing_sufficient() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();
        String q = "dime qué actas tienen 20 asistentes";

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", q, List.of()),
                        Optional.of(QueryType.FILTER_AND_LIST),
                        QueryType.FILTER_AND_LIST.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(q, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
    }

    @Test
    void assess_detectsIncompleteTrailingPreposition_q6() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "qué se habla de cámaras en ?", List.of()),
                        Optional.of(QueryType.FIND_PARAGRAPH),
                        QueryType.FIND_PARAGRAPH.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback("qué se habla de cámaras en ?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("time_reference");
    }

    @Test
    void assess_detectsIncompleteCountFilter_q7() {
        DefaultAmbiguityAssessmentService sut = new DefaultAmbiguityAssessmentService();

        AmbiguityAssessment out =
                sut.assess(
                        new NormalizedQuery("raw", "cuenta las actas en las que", List.of()),
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        QueryType.COUNT_DOCUMENTS.name(),
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback("cuenta las actas en las que", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(out.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(out.missingFields()).contains("filter_condition");
    }
}

