package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReasoningBlockSanitizerTest {

    @Test
    void stripsThinkTagsFromFinalAnswer() {
        String raw =
                "<think>Debo buscar en el acta del 24/02/2025.</think>"
                        + "El presidente fue Juan Pérez Gutiérrez.";

        String out = ReasoningBlockSanitizer.stripReasoningBlocks(raw);

        assertThat(out).isEqualTo("El presidente fue Juan Pérez Gutiérrez.");
        assertThat(out).doesNotContain("redacted_thinking");
    }

    @Test
    void stripsThoughtActionObservationBlocks() {
        String raw =
                """
                Thought: Necesito el campo presidente del acta anclada.
                Action: GET_FIELD
                Observation: metadata devolvió un nombre.
                El presidente de la reunión fue Juan Pérez Gutiérrez.
                """;

        String out = ReasoningBlockSanitizer.stripReasoningBlocks(raw);

        assertThat(out).isEqualTo("El presidente de la reunión fue Juan Pérez Gutiérrez.");
        assertThat(out).doesNotContain("Thought:");
        assertThat(out).doesNotContain("Action:");
        assertThat(out).doesNotContain("Observation:");
    }

    @Test
    void stripsRedactedReasoningBlocks() {
        String raw =
                """
                redacted_reasoning
                Reasoning: comparar fechas entre actas.
                La secretaria fue Rosa Aguilar Fernández.
                """;

        String out = ReasoningBlockSanitizer.stripReasoningBlocks(raw);

        assertThat(out).isEqualTo("La secretaria fue Rosa Aguilar Fernández.");
        assertThat(out).doesNotContain("redacted_reasoning");
        assertThat(out).doesNotContain("Reasoning:");
    }

    @Test
    void keepsFactualAnswerAfterReasoningBlock() {
        String raw =
                """
                
                Analizo asistentes y horarios.
                
                Los asistentes fueron 20 propietarios y la reunión terminó a las 20:30 h.
                DETERMINISTIC_TOOL_ROUTE outcome=SUCCEEDED
                """;

        String out = ReasoningBlockSanitizer.stripReasoningBlocks(raw);

        assertThat(out)
                .contains("Los asistentes fueron 20 propietarios")
                .contains("20:30 h");
        assertThat(out).doesNotContain("DETERMINISTIC_TOOL_ROUTE");
    }

    @Test
    void emptyAfterSanitizationReturnsControlledFallback() {
        String raw =
                """
                <think>Solo razonamiento interno sin respuesta factual.</think>
                Thought: no hay respuesta final.
                {"workflowName":"deterministic-tool","routingRouteKind":"DETERMINISTIC_TOOL_ROUTE"}
                """;

        String out = ReasoningBlockSanitizer.sanitizeForUser(raw);

        assertThat(out).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void stripsInternalJsonPlanWithoutRemovingFactualSentence() {
        String raw =
                """
                {"workflowName":"clarification","routingRouteKind":"DIRECT_WORKFLOW_ROUTE","predictedQueryType":"GET_FIELD"}
                En el acta del 24 de febrero de 2025 asistieron 20 personas.
                """;

        String out = ReasoningBlockSanitizer.stripReasoningBlocks(raw);

        assertThat(out).isEqualTo("En el acta del 24 de febrero de 2025 asistieron 20 personas.");
    }
}
