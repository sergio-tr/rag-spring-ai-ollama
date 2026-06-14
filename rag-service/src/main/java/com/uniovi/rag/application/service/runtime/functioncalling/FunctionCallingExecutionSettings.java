package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;

/** Resolves function-calling execution mode flags from runtime configuration. */
public final class FunctionCallingExecutionSettings {

    private FunctionCallingExecutionSettings() {}

    public record Settings(boolean backendProposalEnabled, boolean nativeProviderEnabled) {}

    public static Settings from(RagConfig rag) {
        boolean fcOn = rag.functionCallingEnabled();
        return new Settings(
                fcOn && rag.functionCallingBackendProposalEnabled(),
                fcOn && rag.functionCallingNativeProviderEnabled());
    }

    public static Settings from(ExecutionContext ctx) {
        return from(ctx.resolved().toRagConfig());
    }
}
