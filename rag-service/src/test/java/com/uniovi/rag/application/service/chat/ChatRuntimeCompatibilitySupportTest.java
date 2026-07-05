package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.interfaces.rest.dto.DisabledRuntimeFeatureDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRuntimeCompatibilitySupportTest {

    private static RuntimeConfigCapabilityDto chatToggle(
            String key, List<String> requires, List<String> excludes, boolean engineWired) {
        return new RuntimeConfigCapabilityDto(
                key,
                key,
                "",
                "RUNTIME_HOT_SWAPPABLE",
                true,
                true,
                true,
                engineWired,
                null,
                1,
                requires,
                excludes,
                false,
                false,
                null,
                null);
    }

    private static RuntimeIndexCompatibilityDto structuredSearchIndex() {
        return new RuntimeIndexCompatibilityDto(
                UUID.randomUUID(),
                null,
                "hash",
                Map.of("materializationStrategy", "STRUCTURED_SEARCH"),
                true,
                new RuntimeSnapshotCapabilitiesDto("STRUCTURED_SEARCH", false, "emb", 400, 40),
                null,
                true,
                "COMPATIBLE");
    }

    private static DisabledRuntimeFeatureDto findDisabled(List<DisabledRuntimeFeatureDto> list, String key) {
        return list.stream().filter(d -> key.equals(d.key())).findFirst().orElse(null);
    }

    @Test
    void structuredSearch_disablesRetrievalStack() {
        List<RuntimeConfigCapabilityDto> caps =
                List.of(
                        chatToggle("useRetrieval", List.of(), List.of(), true),
                        chatToggle("naiveFullCorpusInPromptEnabled", List.of(), List.of("useRetrieval"), true),
                        chatToggle("expansionEnabled", List.of(), List.of(), true),
                        chatToggle("useAdvisor", List.of("useRetrieval"), List.of(), true),
                        chatToggle("rankerEnabled", List.of("useRetrieval"), List.of(), true),
                        chatToggle("postRetrievalEnabled", List.of("useRetrieval"), List.of(), true),
                        chatToggle("toolsEnabled", List.of(), List.of(), true),
                        chatToggle("functionCallingEnabled", List.of(), List.of("naiveFullCorpusInPromptEnabled"), true),
                        chatToggle("memoryEnabled", List.of(), List.of(), true),
                        chatToggle("clarificationEnabled", List.of(), List.of(), true));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps, Map.of("useRetrieval", false), structuredSearchIndex());

        assertThat(findDisabled(disabled, "useRetrieval").reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED);
        assertThat(findDisabled(disabled, "naiveFullCorpusInPromptEnabled").reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED);
        assertThat(findDisabled(disabled, "expansionEnabled")).isNull();
        assertThat(findDisabled(disabled, "useAdvisor").reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED);
        assertThat(findDisabled(disabled, "rankerEnabled").reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED);
        assertThat(findDisabled(disabled, "postRetrievalEnabled").reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED);
    }

    @Test
    void structuredSearch_doesNotDisableDirectPathFeatures() {
        List<RuntimeConfigCapabilityDto> caps =
                List.of(
                        chatToggle("toolsEnabled", List.of(), List.of(), true),
                        chatToggle("functionCallingEnabled", List.of(), List.of("naiveFullCorpusInPromptEnabled"), true),
                        chatToggle("expansionEnabled", List.of(), List.of(), true),
                        chatToggle("nerEnabled", List.of(), List.of(), true),
                        chatToggle("reasoningEnabled", List.of(), List.of(), true),
                        chatToggle("adaptiveRoutingEnabled", List.of(), List.of(), true),
                        chatToggle("judgeEnabled", List.of(), List.of(), true),
                        chatToggle("memoryEnabled", List.of(), List.of(), true),
                        chatToggle("clarificationEnabled", List.of(), List.of(), true));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps, Map.of(), structuredSearchIndex());

        for (String key :
                List.of(
                        "toolsEnabled",
                        "functionCallingEnabled",
                        "expansionEnabled",
                        "nerEnabled",
                        "reasoningEnabled",
                        "adaptiveRoutingEnabled",
                        "judgeEnabled",
                        "memoryEnabled",
                        "clarificationEnabled")) {
            assertThat(findDisabled(disabled, key)).isNull();
        }
    }

    @Test
    void chunkLevel_allowsExpansionWhenEngineWired() {
        List<RuntimeConfigCapabilityDto> caps = List.of(chatToggle("expansionEnabled", List.of(), List.of(), true));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps,
                        Map.of(),
                        new RuntimeIndexCompatibilityDto(
                                UUID.randomUUID(),
                                null,
                                "hash",
                                Map.of("materializationStrategy", "CHUNK_LEVEL"),
                                true,
                                new RuntimeSnapshotCapabilitiesDto("CHUNK_LEVEL", false, "emb", 400, 40),
                                null,
                                true,
                                "COMPATIBLE"));

        assertThat(findDisabled(disabled, "expansionEnabled")).isNull();
    }

    @Test
    void expansionNotEngineWired_usesGenericNotImplementedReason() {
        List<RuntimeConfigCapabilityDto> caps = List.of(chatToggle("expansionEnabled", List.of(), List.of(), false));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps, Map.of(), null);

        assertThat(findDisabled(disabled, "expansionEnabled").reasonCode()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void advisorRankerPostRetrieval_requireUseRetrievalOnVectorProjects() {
        List<RuntimeConfigCapabilityDto> caps =
                List.of(
                        chatToggle("useAdvisor", List.of("useRetrieval"), List.of(), true),
                        chatToggle("rankerEnabled", List.of("useRetrieval"), List.of(), true),
                        chatToggle("postRetrievalEnabled", List.of("useRetrieval"), List.of(), true));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps,
                        Map.of("useRetrieval", false),
                        new RuntimeIndexCompatibilityDto(
                                null,
                                null,
                                null,
                                Map.of(),
                                false,
                                new RuntimeSnapshotCapabilitiesDto("HYBRID", true, "emb", 400, 40),
                                null,
                                true,
                                "COMPATIBLE"));

        assertThat(findDisabled(disabled, "useAdvisor").reasonCode()).isEqualTo("REQUIRES_useRetrieval");
        assertThat(findDisabled(disabled, "rankerEnabled").reasonCode()).isEqualTo("REQUIRES_useRetrieval");
        assertThat(findDisabled(disabled, "postRetrievalEnabled").reasonCode()).isEqualTo("REQUIRES_useRetrieval");
    }

    @Test
    void presetBaseFeature_locksEnabledFeatures() {
        List<RuntimeConfigCapabilityDto> caps = List.of(chatToggle("useRetrieval", List.of(), List.of(), true));
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps, p3, null, p3);

        assertThat(findDisabled(disabled, "useRetrieval").reasonCode())
                .isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED);
    }

    @Test
    void presetControlledOffFeature_defersOptionalToggle() {
        List<RuntimeConfigCapabilityDto> caps = List.of(chatToggle("expansionEnabled", List.of(), List.of(), true));
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps, p3, null, p3);

        assertThat(findDisabled(disabled, "expansionEnabled").reasonCode())
                .isEqualTo(PresetBaseFeatureSupport.PRESET_FEATURE_TOGGLE_DEFERRED);
    }

    @Test
    void functionCalling_excludesNaiveFullCorpus() {
        List<RuntimeConfigCapabilityDto> caps =
                List.of(chatToggle("functionCallingEnabled", List.of(), List.of("naiveFullCorpusInPromptEnabled"), true));

        var disabled =
                ChatRuntimeCompatibilitySupport.disabledRuntimeFeatures(
                        caps,
                        Map.of("naiveFullCorpusInPromptEnabled", true),
                        null);

        assertThat(findDisabled(disabled, "functionCallingEnabled").reasonCode()).isEqualTo("EXCLUDES_naiveFullCorpusInPromptEnabled");
    }
}
