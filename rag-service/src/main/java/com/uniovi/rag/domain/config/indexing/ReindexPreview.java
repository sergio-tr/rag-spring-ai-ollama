package com.uniovi.rag.domain.config.indexing;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Indicates whether persisted corpus artifacts need rebuilding after a configuration change.
 */
public record ReindexPreview(boolean requiresReindex, List<String> reasons) {

    public static ReindexPreview fromCapabilityDiff(CapabilitySet before, CapabilitySet after) {
        List<String> reasons = new ArrayList<>();
        if (CapabilitySet.differsForReindex(before, after)) {
            reasons.add("Capability set or embedding/index parameters changed");
        }
        return new ReindexPreview(!reasons.isEmpty(), List.copyOf(reasons));
    }

    /**
     * When profile kinds that affect persisted ingestion/index are touched, reindex is required.
     */
    public static ReindexPreview fromTouchedProfileTypes(Set<ConfigProfileType> touched) {
        EnumSet<ConfigProfileType> triggers =
                EnumSet.of(
                        ConfigProfileType.METADATA,
                        ConfigProfileType.CHUNKING,
                        ConfigProfileType.EMBEDDING,
                        ConfigProfileType.INDEX,
                        ConfigProfileType.INGESTION_LLM);
        List<String> reasons = new ArrayList<>();
        for (ConfigProfileType t : touched) {
            if (triggers.contains(t)) {
                reasons.add("Profile type " + t + " affects persisted corpus or index");
            }
        }
        return new ReindexPreview(!reasons.isEmpty(), List.copyOf(reasons));
    }
}
