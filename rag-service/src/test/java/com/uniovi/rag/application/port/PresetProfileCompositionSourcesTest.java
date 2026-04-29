package com.uniovi.rag.application.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresetProfileCompositionSourcesTest {

    @Test
    void compactConstructorCopiesInputsAndTreatsNullAsEmpty() {
        UUID pid = UUID.randomUUID();
        PresetProfileCompositionSources src =
                new PresetProfileCompositionSources(null, null, List.of(pid));

        assertThat(src.presetValues()).isEmpty();
        assertThat(src.orderedProfilePayloads()).isEmpty();
        assertThat(src.profileIds()).containsExactly(pid);
    }

    @Test
    void compactConstructorDefensivelyCopiesCollections() {
        UUID pid = UUID.randomUUID();
        Map<String, Object> preset = Map.of("k", 1);
        List<Map<String, Object>> payloads = List.of(Map.of("a", 2));
        List<UUID> ids = new ArrayList<>(List.of(pid));

        PresetProfileCompositionSources src =
                new PresetProfileCompositionSources(preset, payloads, ids);

        ids.clear();

        assertThat(src.profileIds()).containsExactly(pid);
        assertThat(src.presetValues()).containsEntry("k", 1);
        assertThat(src.orderedProfilePayloads()).hasSize(1);
    }
}
