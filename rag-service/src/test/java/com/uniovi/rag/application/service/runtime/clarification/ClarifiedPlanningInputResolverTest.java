package com.uniovi.rag.application.service.runtime.clarification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClarifiedPlanningInputResolverTest {

    @Test
    void resolve_mergesBaseAndAnswerIntoPlanningQuery() {
        String merged =
                "BASE:¿Cuántos participantes asistieron?\n"
                        + "QUESTION:¿A qué acta o reunión te refieres? Indica la fecha o el documento.\n"
                        + "ANSWER:La reunión del 25/02/2026";
        assertThat(ClarifiedPlanningInputResolver.resolveForPlanning(merged))
                .isEqualTo("¿Cuántos participantes asistieron (La reunión del 25/02/2026)?");
    }

    @Test
    void resolve_passthrough_whenNotMergedPayload() {
        assertThat(ClarifiedPlanningInputResolver.resolveForPlanning("plain query"))
                .isEqualTo("plain query");
    }
}
