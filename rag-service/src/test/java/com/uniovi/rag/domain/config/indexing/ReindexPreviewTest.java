package com.uniovi.rag.domain.config.indexing;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReindexPreviewTest {

    @Test
    void fromCapabilityDiff_whenDifferent_returnsRequiresReindexWithReason() {
        CapabilitySet before = new CapabilitySet(Set.of(), "e1", "DEFAULT", "DEFAULT");
        CapabilitySet after = new CapabilitySet(Set.of(), "e2", "DEFAULT", "DEFAULT");

        ReindexPreview p = ReindexPreview.fromCapabilityDiff(before, after);

        assertThat(p.requiresReindex()).isTrue();
        assertThat(p.reasons()).containsExactly("Capability set or embedding/index parameters changed");
    }

    @Test
    void fromTouchedProfileTypes_whenOnlyNonTriggerProfiles_returnsNoReindex() {
        Set<ConfigProfileType> touched = EnumSet.of(ConfigProfileType.PROMPT_TECHNICAL, ConfigProfileType.RAG_RUNTIME_FLAGS);

        ReindexPreview p = ReindexPreview.fromTouchedProfileTypes(touched);

        assertThat(p.requiresReindex()).isFalse();
        assertThat(p.reasons()).isEmpty();
    }

    @Test
    void fromTouchedProfileTypes_whenTriggerProfileTouched_returnsReasonsPerTrigger() {
        Set<ConfigProfileType> touched = EnumSet.of(ConfigProfileType.METADATA, ConfigProfileType.INDEX);

        ReindexPreview p = ReindexPreview.fromTouchedProfileTypes(touched);

        assertThat(p.requiresReindex()).isTrue();
        assertThat(p.reasons())
                .contains("Profile type METADATA affects persisted corpus or index")
                .contains("Profile type INDEX affects persisted corpus or index");
    }
}

