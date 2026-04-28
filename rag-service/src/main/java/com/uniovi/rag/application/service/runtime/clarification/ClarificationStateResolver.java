package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.port.PendingClarificationLoad;
import com.uniovi.rag.application.port.PendingClarificationStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Loads pending clarification JSON, recovers invalid state, applies {@link ClarifiedQueryRefiner}. No QU, no LLM.
 */
@Service
public class ClarificationStateResolver {

    private final PendingClarificationStore pendingClarificationStore;
    private final ClarifiedQueryRefiner clarifiedQueryRefiner;
    private final ClarificationStrategy clarificationStrategy;

    public ClarificationStateResolver(
            PendingClarificationStore pendingClarificationStore,
            ClarifiedQueryRefiner clarifiedQueryRefiner,
            ClarificationStrategy clarificationStrategy) {
        this.pendingClarificationStore = pendingClarificationStore;
        this.clarifiedQueryRefiner = clarifiedQueryRefiner;
        this.clarificationStrategy = clarificationStrategy;
    }

    public ClarificationBootstrap bootstrap(UUID conversationId, String userQuery) {
        String uq = userQuery == null ? "" : userQuery;
        if (conversationId == null) {
            return new ClarificationBootstrap(uq, false, false, false);
        }
        PendingClarificationLoad load = pendingClarificationStore.load(conversationId);
        if (load.invalidJsonOrVersion()) {
            clarificationStrategy.clearInvalidPending(conversationId);
            return new ClarificationBootstrap(uq, false, false, true);
        }
        var state = load.state();
        if (state.isEmpty()) {
            return new ClarificationBootstrap(uq, false, false, false);
        }
        String merged = clarifiedQueryRefiner.refine(state.get(), uq);
        return new ClarificationBootstrap(merged, true, true, false);
    }
}
