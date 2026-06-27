package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.service.runtime.query.DefaultAmbiguityAssessmentService;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ClarificationLoopTest {

    private final DefaultAmbiguityAssessmentService ambiguity = new DefaultAmbiguityAssessmentService();
    private final ClarificationPolicyResolver policy =
            new ClarificationPolicyResolver(new ClarificationQuestionGenerator());

    @Test
    void ambiguousParticipantsQuestion_asksForActaDate() {
        AmbiguityAssessment assessment =
                ambiguity.assess(
                        new NormalizedQuery("raw", "¿cuántos participantes asistieron?", List.of()),
                        Optional.empty(),
                        "UNCLASSIFIED",
                        ClassifierStatus.LOW_CONFIDENCE,
                        StructuredRewriteResult.identityFallback("¿Cuántos participantes asistieron?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.MISSING_INFORMATION);
        assertThat(assessment.missingFields()).contains("time_reference");

        ClarificationDecision decision =
                policy.resolve(
                        ClarificationPolicyResolverTestSupport.ctx("¿Cuántos participantes asistieron?"),
                        ClarificationPolicyResolverTestSupport.plan(assessment));
        assertThat(decision.ask()).isTrue();
        assertThat(decision.terminalOutcome()).isEqualTo(ClarificationOutcome.ASKED_CLARIFICATION);
        assertThat(decision.questionIfAsking().questionKind()).isEqualTo(ClarificationQuestionKind.MISSING_DATE);
        assertThat(decision.questionIfAsking().questionText())
                .containsIgnoringCase("acta")
                .containsIgnoringCase("reunión");
    }

    @Test
    void exactDateParticipantsQuestion_doesNotAskClarification() {
        AmbiguityAssessment assessment =
                ambiguity.assess(
                        new NormalizedQuery(
                                "raw",
                                "¿cuántos participantes asistieron en el acta del 25/02/2026?",
                                List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        "GET_FIELD",
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(
                                "¿Cuántos participantes asistieron en el acta del 25/02/2026?", null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);

        ClarificationDecision decision =
                policy.resolve(
                        ClarificationPolicyResolverTestSupport.ctx(
                                "¿Cuántos participantes asistieron en el acta del 25/02/2026?"),
                        ClarificationPolicyResolverTestSupport.plan(assessment));
        assertThat(decision.ask()).isFalse();
        assertThat(decision.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
    }

    @Test
    void clarificationAnswer_resolvesOriginalIntentForPlanning() {
        String merged =
                "BASE:¿Cuántos participantes asistieron?\n"
                        + "QUESTION:¿A qué acta o reunión te refieres? Indica la fecha o el documento.\n"
                        + "ANSWER:La reunión del 25/02/2026";
        String resolved = ClarifiedPlanningInputResolver.resolveForPlanning(merged);

        AmbiguityAssessment assessment =
                ambiguity.assess(
                        new NormalizedQuery("raw", resolved.toLowerCase(), List.of()),
                        Optional.of(QueryType.GET_FIELD),
                        "GET_FIELD",
                        ClassifierStatus.OK,
                        StructuredRewriteResult.identityFallback(resolved, null),
                        EntityExtractionResult.emptyWithNote(null));

        assertThat(assessment.status()).isEqualTo(AmbiguityStatus.SUFFICIENT);
        assertThat(resolved).contains("25/02/2026");
        assertThat(resolved).containsIgnoringCase("participantes");
    }
}
