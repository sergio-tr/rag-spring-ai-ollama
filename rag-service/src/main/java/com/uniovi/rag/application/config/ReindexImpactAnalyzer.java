package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalizes transitional profile touches and capability diffs into {@link ReindexImpact}.
 */
@Component
public class ReindexImpactAnalyzer {

    private static final EnumSet<ConfigProfileType> HARD_PROFILE_TRIGGERS =
            EnumSet.of(
                    ConfigProfileType.CHUNKING,
                    ConfigProfileType.EMBEDDING,
                    ConfigProfileType.INDEX,
                    ConfigProfileType.INGESTION_LLM);

    public ReindexImpact analyze(
            CapabilitySet before, CapabilitySet after, Set<ConfigProfileType> touchedProfileTypes) {
        Set<String> reasonSet = new LinkedHashSet<>();
        boolean hard = false;

        if (touchedProfileTypes != null) {
            for (ConfigProfileType t : touchedProfileTypes) {
                if (HARD_PROFILE_TRIGGERS.contains(t)) {
                    hard = true;
                    reasonSet.add("HARD:" + t.name());
                }
            }
        }

        if (before != null && after != null && CapabilitySet.differsForReindex(before, after)) {
            hard = true;
            reasonSet.add("HARD:CAPABILITY_DIFF");
        }

        if (hard) {
            return new ReindexImpact(ReindexImpactLevel.HARD_REINDEX, List.copyOf(reasonSet));
        }

        if (touchedProfileTypes != null && touchedProfileTypes.contains(ConfigProfileType.METADATA)) {
            return new ReindexImpact(ReindexImpactLevel.SOFT_REINDEX, List.of("SOFT:METADATA"));
        }

        return ReindexImpact.none();
    }
}
