package com.uniovi.rag.testsupport;
import com.uniovi.rag.testsupport.ConversationRecallGuardTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.memory.ConversationHistoryLoader;
import com.uniovi.rag.application.service.runtime.memory.ConversationRecallGuard;
import java.util.List;

/** Test doubles for {@link ConversationRecallGuard}. */
public final class ConversationRecallGuardTestSupport {

    private ConversationRecallGuardTestSupport() {}

    /** Guard that never short-circuits (default for orchestrator unit tests). */
    public static ConversationRecallGuard neverShortCircuit() {
        ConversationRecallGuard guard = mock(ConversationRecallGuard.class);
        when(guard.shouldShortCircuit(any())).thenReturn(false);
        when(guard.shouldShortCircuitAmbiguousActaQuery(any())).thenReturn(false);
        return guard;
    }

    /** Real guard backed by an empty conversation history loader. */
    public static ConversationRecallGuard withEmptyHistory() {
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        when(loader.loadEligibleHistory(any())).thenReturn(List.of());
        return new ConversationRecallGuard(loader);
    }
}
