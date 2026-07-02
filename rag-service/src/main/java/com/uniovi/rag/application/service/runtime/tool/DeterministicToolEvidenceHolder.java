package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.Minute;
import java.util.List;
import java.util.Optional;

/**
 * Thread-local carrier for metadata-tool matched minutes between tool execution and retrieval packing.
 */
public final class DeterministicToolEvidenceHolder {

    public record Evidence(List<Minute> matchedMinutes, String assembledContextText, boolean highConfidence) {}

    private static final ThreadLocal<Evidence> CURRENT = new ThreadLocal<>();

    private DeterministicToolEvidenceHolder() {}

    public static void set(Evidence evidence) {
        if (evidence == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(evidence);
        }
    }

    public static Optional<Evidence> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
