package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.port.PendingClarificationLoad;
import com.uniovi.rag.application.port.PendingClarificationStore;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClarificationStateResolverTest {

    @Mock
    private PendingClarificationStore pendingClarificationStore;

    @Mock
    private ClarificationStrategy clarificationStrategy;

    @Test
    void bootstrap_noConversation_returnsUserQueryOnly() {
        ClarificationStateResolver resolver =
                new ClarificationStateResolver(
                        pendingClarificationStore, new ClarifiedQueryRefiner(), clarificationStrategy);
        ClarificationBootstrap b = resolver.bootstrap(null, "hello");
        assertThat(b.effectivePlanningInputText()).isEqualTo("hello");
        assertThat(b.pendingClarificationLoadedForTrace()).isFalse();
        assertThat(b.validPendingExistedAtLoad()).isFalse();
        assertThat(b.invalidPendingRecoveredThisTurn()).isFalse();
    }

    @Test
    void bootstrap_invalidJson_clearsAndUsesRawUserQuery() {
        UUID conv = UUID.randomUUID();
        when(pendingClarificationStore.load(conv)).thenReturn(PendingClarificationLoad.invalid());

        ClarificationStateResolver resolver =
                new ClarificationStateResolver(
                        pendingClarificationStore, new ClarifiedQueryRefiner(), clarificationStrategy);

        ClarificationBootstrap b = resolver.bootstrap(conv, "turn");
        assertThat(b.effectivePlanningInputText()).isEqualTo("turn");
        assertThat(b.invalidPendingRecoveredThisTurn()).isTrue();
        verify(clarificationStrategy).clearInvalidPending(eq(conv));
    }

    @Test
    void bootstrap_validPending_mergesWithRefiner() {
        UUID conv = UUID.randomUUID();
        PendingClarificationState pending =
                new PendingClarificationState(
                        PendingClarificationState.SCHEMA_VERSION,
                        UUID.randomUUID(),
                        "base q",
                        ClarificationQuestionKind.MISSING_TOPIC.templateText(),
                        ClarificationQuestionKind.MISSING_TOPIC,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        "corr");
        when(pendingClarificationStore.load(conv)).thenReturn(PendingClarificationLoad.ok(pending));

        ClarificationStateResolver resolver =
                new ClarificationStateResolver(
                        pendingClarificationStore, new ClarifiedQueryRefiner(), clarificationStrategy);

        ClarificationBootstrap b = resolver.bootstrap(conv, "physics");
        assertThat(b.pendingClarificationLoadedForTrace()).isTrue();
        assertThat(b.validPendingExistedAtLoad()).isTrue();
        assertThat(b.effectivePlanningInputText()).contains("BASE:base q");
        assertThat(b.effectivePlanningInputText()).contains("ANSWER:physics");
    }
}
