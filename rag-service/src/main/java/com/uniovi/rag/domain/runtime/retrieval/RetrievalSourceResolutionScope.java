package com.uniovi.rag.domain.runtime.retrieval;

import java.util.Optional;

/**
 * Associates the current servlet/request thread with the resolved {@code retrievalOverrideMode}
 * ({@code preset} / {@code project_settings} / {@code assistant_defaults} / {@code custom}) so it can be surfaced in
 * retrieval trace/telemetry even though it is resolved far upstream of {@link AdvancedRetrievalPipeline}
 * (see phase-4-4 effective config trace: {@code retrievalSourceMode}).
 */
public final class RetrievalSourceResolutionScope {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RetrievalSourceResolutionScope() {}

    public static void bind(String mode) {
        if (mode == null || mode.isBlank()) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(mode);
    }

    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
