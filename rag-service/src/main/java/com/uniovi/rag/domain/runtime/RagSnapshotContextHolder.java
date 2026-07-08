package com.uniovi.rag.domain.runtime;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Per-request active knowledge snapshot ids for metadata-tool corpus scoping.
 * Populated from {@link com.uniovi.rag.domain.runtime.engine.ExecutionContext#knowledgeSnapshotSelection()}.
 */
public final class RagSnapshotContextHolder {

    private static final ThreadLocal<List<String>> CURRENT = new ThreadLocal<>();

    private RagSnapshotContextHolder() {}

    public static void set(List<UUID> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(
                snapshotIds.stream()
                        .filter(id -> id != null)
                        .map(UUID::toString)
                        .distinct()
                        .toList());
    }

    public static Set<String> activeSnapshotIds() {
        List<String> ids = CURRENT.get();
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream().collect(Collectors.toUnmodifiableSet());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
