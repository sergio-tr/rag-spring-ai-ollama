package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedRuntimeConfigResponseDtoTest {

    @Test
    void fromDomain_blankPromptWhenNullAndUsesCoreWhenProjectionAbsent() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig core = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "a", "b", "c", "simple");
        ResolvedRuntimeConfig resolved = new ResolvedRuntimeConfig(
                core,
                CapabilitySet.fromRagConfig(core),
                CompatibilityResult.ok(),
                ReindexImpact.none(),
                SystemPromptLayers.empty(),
                null,
                new ConfigProvenance(null, null, null, List.of(), null, null),
                null);

        ResolvedRuntimeConfigResponseDto dto = ResolvedRuntimeConfigResponseDto.fromDomain(resolved);

        assertThat(dto.effectiveSystemPrompt()).isEmpty();
        Map<String, Object> projection = dto.configProjection();
        assertThat(projection).isEqualTo(core.toValueMap());
    }

    @Test
    void fromDomain_usesExplicitProjectionMapWhenPresent() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig core = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "a", "b", "c", "simple");
        RagFeatureConfiguration fcProjection = new RagFeatureConfiguration();
        RagConfig projection =
                RagConfig.fromFeatureConfiguration(fcProjection, 11, 0.8, "la", "lb", "lc", "dense");
        ResolvedRuntimeConfig resolved = new ResolvedRuntimeConfig(
                core,
                CapabilitySet.fromRagConfig(core),
                CompatibilityResult.ok(),
                ReindexImpact.none(),
                SystemPromptLayers.empty(),
                "prompt",
                new ConfigProvenance(null, null, null, List.of(), null, null),
                projection);

        ResolvedRuntimeConfigResponseDto dto = ResolvedRuntimeConfigResponseDto.fromDomain(resolved);

        assertThat(dto.effectiveSystemPrompt()).isEqualTo("prompt");
        assertThat(dto.configProjection()).isEqualTo(projection.toValueMap());
    }
}
