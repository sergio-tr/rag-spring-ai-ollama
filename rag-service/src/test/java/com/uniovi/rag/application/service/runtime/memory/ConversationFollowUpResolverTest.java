package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationFollowUpResolverTest {

    private static final List<ConversationMemoryTurn> SINGLE_ACTA_FEB_24_HISTORY =
            List.of(
                    new ConversationMemoryTurn(
                            UUID.randomUUID(),
                            1,
                            MessageRole.USER,
                            "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?"),
                    new ConversationMemoryTurn(
                            UUID.randomUUID(),
                            2,
                            MessageRole.ASSISTANT,
                            "Asistieron 20 personas."));

    @Test
    void expandsPresidentFollowUpWithPreviousActaDate() {
        assertThat(ConversationFollowUpResolver.expand(SINGLE_ACTA_FEB_24_HISTORY, "¿quién fue el presidente?"))
                .get()
                .asString()
                .containsIgnoringCase("presidente")
                .contains("24 de febrero de 2025");
    }

    @Test
    void expandsSecretaryFollowUpWithPreviousActaDate() {
        assertThat(ConversationFollowUpResolver.expand(SINGLE_ACTA_FEB_24_HISTORY, "y quién fue la secretaria?"))
                .get()
                .asString()
                .containsIgnoringCase("secretaria")
                .contains("24 de febrero de 2025");
    }

    @Test
    void expandsStartEndTimeFollowUpWithPreviousActaDate() {
        assertThat(
                        ConversationFollowUpResolver.expand(
                                SINGLE_ACTA_FEB_24_HISTORY, "¿a qué hora empezó y a qué hora terminó esa acta?"))
                .get()
                .asString()
                .contains("24 de febrero de 2025");
    }

    @Test
    void expandsTopicsFollowUpWithPreviousActaDate() {
        assertThat(ConversationFollowUpResolver.expand(SINGLE_ACTA_FEB_24_HISTORY, "¿cuáles fueron los temas?"))
                .get()
                .asString()
                .containsIgnoringCase("temas")
                .contains("24 de febrero de 2025");
    }

    @Test
    void asksClarificationWhenMultipleStructuredActaAnchorsExist() {
        List<ConversationMemoryTurn> multiDateHistory =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.ASSISTANT,
                                "Acta A",
                                Map.of(ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE, "2025-02-24")),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Acta B",
                                Map.of(ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE, "2026-02-25")));

        assertThat(ConversationFollowUpResolver.expand(multiDateHistory, "¿quién fue el presidente?"))
                .isEmpty();
        assertThat(ConversationFollowUpResolver.hasMultipleDistinctAnchorDates(multiDateHistory)).isTrue();

        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(multiDateHistory);
        ConversationRecallGuard guard = new ConversationRecallGuard(loader);

        assertThat(guard.shouldShortCircuitAmbiguousActaQuery(ctxWithQuery("¿quién fue el presidente?")))
                .isTrue();
    }

    private static ExecutionContext ctxWithQuery(String userQuery) {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        5,
                        0.2,
                        "l",
                        "e",
                        "c",
                        "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID id = UUID.randomUUID();
        return new ExecutionContext(
                id,
                id,
                id,
                userQuery,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                userQuery,
                userQuery,
                Optional.empty(),
                ConversationMemoryOutcome.CONDENSE_FAILED_FALLBACK,
                List.of(),
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    @Test
    void expand_esaReunion_usesMostRecentDateFromHistory() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "El presidente en 25/02/2026 fue Jorge Moreno Navarro."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Cuántos participantes asistieron a esa reunión?"))
                .get()
                .asString()
                .contains("25/02/2026")
                .contains("la reunión del");
    }

    @Test
    void expand_esaReunion_afterPresidentQuestion_anchorsUserDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Jorge Moreno Navarro fue el presidente."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Cuántos participantes asistieron a esa reunión?"))
                .get()
                .asString()
                .isEqualTo("¿Cuántos participantes asistieron a la reunión del 25/02/2026?");
    }

    @Test
    void expand_ellos_whenParticipantsQuestion() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.ASSISTANT,
                                "En el acta del 25 de febrero de 2026 asistieron 17 participantes."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Quiénes fueron ellos?"))
                .get()
                .asString()
                .contains("25 de febrero de 2026");
    }

    @Test
    void expand_ellos_prefersUserTurnDateOverAssistantWrongDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "En el acta del 25/02/2025 asistieron 20 participantes."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Quiénes fueron ellos?"))
                .get()
                .asString()
                .contains("25/02/2026")
                .doesNotContain("25/02/2025");
    }

    @Test
    void expand_presidentFollowUp_appendsActaDateFromHistory() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Asistieron 12 personas."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿quién fue el presidente?"))
                .get()
                .asString()
                .contains("presidente")
                .contains("24 de febrero de 2025");
    }

    @Test
    void expand_secretaryFollowUp_appendsActaDateFromHistory() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(), 2, MessageRole.ASSISTANT, "Lista de asistentes."));

        assertThat(ConversationFollowUpResolver.expand(history, "y quién fue la secretaria?"))
                .get()
                .asString()
                .contains("secretaria")
                .contains("24 de febrero de 2025");
    }

    @Test
    void expand_multipleDatesInHistory_prefersMostRecentUserDateForRoleFollowUp() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "En el acta del 24/02/2025 fue otro presidente."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿quién fue el presidente?"))
                .get()
                .asString()
                .contains("25/02/2026");
    }

    @Test
    void expand_prefersMostRecentStructuredAnchorOverConflictingTextDates() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "En el acta del 25/02/2026 hubo 18 asistentes.",
                                Map.of(
                                        ConversationMemoryAnchorMetadata.ANCHORED_ACTA_DATE,
                                        "2025-02-24")));

        assertThat(ConversationFollowUpResolver.expand(history, "¿quién fue el presidente?"))
                .get()
                .asString()
                .contains("2025-02-24");
    }

    @Test
    void attendeeCountCarryOver_ellipticalDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Cuántos asistentes tiene el acta del 25 de febrero de 2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "El acta del 25 de febrero de 2026 tuvo 17 asistentes."));

        assertThat(ConversationFollowUpResolver.expand(history, "y la del 25 de agosto de 2026"))
                .get()
                .asString()
                .contains("asistentes")
                .contains("25 de agosto de 2026");
    }

    @Test
    void attendeeCountCarryOver_ellipticalDateDelYear() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Cuántos asistentes tiene el acta del 25 de febrero de 2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "El acta del 25 de febrero de 2026 tuvo 17 asistentes."));

        assertThat(ConversationFollowUpResolver.expand(history, "y la del 25 de agosto del 2025"))
                .get()
                .asString()
                .contains("asistentes")
                .contains("25 de agosto del 2025");
    }

    @Test
    void expand_bareParticipantCount_withoutHistory_returnsEmpty() {
        assertThat(ConversationFollowUpResolver.expand(List.of(), "¿Cuántos participantes asistieron?"))
                .isEmpty();
    }

    @Test
    void rewriteCannotOverrideCarriedField_ellipticalSlashDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Cuántos asistentes tiene el acta del 25 de febrero de 2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "17 asistentes."));

        assertThat(ConversationFollowUpResolver.expand(history, "y la del 25/02/25"))
                .get()
                .asString()
                .contains("asistentes")
                .contains("25/02/25");
    }

    @Test
    void topicFollowUp_corpusWideSearch() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "Hazme un resumen de lo que se comenta sobre las zonas comunes"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Resumen de zonas comunes."));

        assertThat(ConversationFollowUpResolver.expand(history, "y qué se dice sobre renovar la pintura"))
                .get()
                .asString()
                .containsIgnoringCase("renovar la pintura")
                .containsIgnoringCase("actas");
    }
}
