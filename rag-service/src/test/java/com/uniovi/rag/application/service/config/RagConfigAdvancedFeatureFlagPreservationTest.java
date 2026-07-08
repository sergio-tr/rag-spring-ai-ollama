package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.chat.ChatRuntimeCompatibilitySupport;
import com.uniovi.rag.application.service.chat.ConversationConfigurationSupport;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.config.RetrievalOverrideModeSupport;
import com.uniovi.rag.domain.config.RetrievalParameterKeys;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.config.RagConfigurationMerge;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RagConfigAdvancedFeatureFlagPreservationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(
            value = RagExperimentalPresetCode.class,
            names = {"P7", "P8", "P9", "P10", "P11", "P12", "P13", "P14", "P15"})
    void sanitize_preservesAdvancedPresetFlags(RagExperimentalPresetCode code) {
        Map<String, Object> product = ChatProductPresetAlignment.effectiveProductRuntimeValues(code);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(product);

        assertThat(sanitized).containsAllEntriesOf(product);
        assertThat(RagConfigValueSanitizer.droppedKeys(product)).isEmpty();
    }

    @Test
    void sanitize_preservesP7DeterministicToolFlags() {
        Map<String, Object> p7 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P7);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(p7);

        assertThat(sanitized)
                .containsEntry("toolsEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", true);
    }

    @Test
    void sanitize_preservesP9FunctionCallingFlags() {
        Map<String, Object> p9 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P9);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(p9);

        assertThat(sanitized)
                .containsEntry("functionCallingEnabled", true)
                .containsEntry("functionCallingBackendProposalEnabled", true);
    }

    @Test
    void sanitize_preservesP10AdvisorFlag() {
        Map<String, Object> p10 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P10);
        assertThat(RagConfigValueSanitizer.sanitize(p10)).containsEntry("useAdvisor", true);
    }

    @Test
    void sanitize_preservesP11AdaptiveRoutingFlag() {
        Map<String, Object> p11 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P11);
        assertThat(RagConfigValueSanitizer.sanitize(p11)).containsEntry("adaptiveRoutingEnabled", true);
    }

    @Test
    void sanitize_preservesP12JudgeFlag() {
        Map<String, Object> p12 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P12);
        assertThat(RagConfigValueSanitizer.sanitize(p12)).containsEntry("judgeEnabled", true);
    }

    @Test
    void sanitize_preservesP13ClarificationFlag() {
        Map<String, Object> p13 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P13);
        assertThat(RagConfigValueSanitizer.sanitize(p13)).containsEntry("clarificationEnabled", true);
    }

    @Test
    void sanitize_preservesP14MemoryFlag() {
        Map<String, Object> p14 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P14);
        assertThat(RagConfigValueSanitizer.sanitize(p14)).containsEntry("memoryEnabled", true);
    }

    @Test
    void sanitize_preservesDemoBestProductionFlags() {
        Map<String, Object> demoBest = ChatProductPresetAlignment.demoBestProductValues();
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(demoBest);

        assertThat(sanitized).containsAllEntriesOf(demoBest);
        assertThat(sanitized)
                .containsEntry("useAdvisor", true)
                .containsEntry("deterministicToolRoutingEnabled", true)
                .containsEntry("functionCallingBackendProposalEnabled", true)
                .containsEntry("clarificationEnabled", true)
                .containsEntry("metadataEnabled", true);
    }

    @Test
    void sanitize_preservesRetrievalOverrideFieldsAtProjectLevel() {
        Map<String, Object> body =
                Map.of(
                        RetrievalParameterKeys.TOP_K,
                        10,
                        RetrievalParameterKeys.SIMILARITY_THRESHOLD,
                        0.5);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);

        assertThat(sanitized).containsEntry(RetrievalParameterKeys.TOP_K, 10);
        assertThat(sanitized).containsEntry(RetrievalParameterKeys.SIMILARITY_THRESHOLD, 0.5);
    }

    @Test
    void sanitize_dropsConversationOnlyRetrievalOverrideModeAtProjectLevel() {
        Map<String, Object> body =
                Map.of(
                        RetrievalOverrideModeSupport.KEY,
                        RetrievalOverrideModeSupport.PROJECT_SETTINGS,
                        RetrievalParameterKeys.TOP_K,
                        8);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(body);

        assertThat(sanitized).containsEntry(RetrievalParameterKeys.TOP_K, 8);
        assertThat(sanitized).doesNotContainKey(RetrievalOverrideModeSupport.KEY);
        assertThat(RagConfigValueSanitizer.droppedKeys(body))
                .containsExactly(RetrievalOverrideModeSupport.KEY);
    }

    @Test
    void conversationMerge_preservesRetrievalOverrideModeAndCustomPair() {
        Map<String, Object> base = Map.of(RetrievalParameterKeys.TOP_K, 8, RetrievalParameterKeys.SIMILARITY_THRESHOLD, 0.1);
        Map<String, Object> patch =
                Map.of(
                        RetrievalOverrideModeSupport.KEY,
                        RetrievalOverrideModeSupport.CUSTOM,
                        RetrievalParameterKeys.TOP_K,
                        2,
                        RetrievalParameterKeys.SIMILARITY_THRESHOLD,
                        0.5);

        Map<String, Object> merged = ConversationConfigurationSupport.mergeConfigPatch(Map.of(), patch, base);

        assertThat(merged)
                .containsEntry(RetrievalOverrideModeSupport.KEY, RetrievalOverrideModeSupport.CUSTOM)
                .containsEntry(RetrievalParameterKeys.TOP_K, 2)
                .containsEntry(RetrievalParameterKeys.SIMILARITY_THRESHOLD, 0.5);
    }

    @Test
    void conversationSanitize_stripsIndexBoundMetadataAndMaterialization() {
        Map<String, Object> raw =
                Map.of(
                        "metadataEnabled",
                        false,
                        "materializationStrategy",
                        "CHUNK_LEVEL",
                        "useAdvisor",
                        true,
                        "topK",
                        8);

        Map<String, Object> snapshot = ConversationConfigurationSupport.sanitizeSnapshot(raw);

        assertThat(snapshot).containsEntry("useAdvisor", true).containsEntry("topK", 8);
        assertThat(snapshot).doesNotContainKeys("metadataEnabled", "materializationStrategy");
        assertThat(ChatRuntimeCompatibilitySupport.findIndexBoundOverrideIssues(raw)).hasSize(2);
    }

    @Test
    void sanitizeThenMerge_producesEquivalentEffectiveRagConfigForAdvancedFlags() throws Exception {
        Map<String, Object> p9 = ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P9);
        Map<String, Object> sanitized = RagConfigValueSanitizer.sanitize(p9);

        RagConfig base =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        8,
                        0.1,
                        "llm",
                        "emb",
                        null,
                        "SIMPLE",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        false,
                        false,
                        false,
                        MaterializationStrategy.CHUNK_LEVEL);

        RagConfig merged =
                RagConfigurationMerge.mergeCascade(
                        base,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(sanitized),
                        Optional.empty(),
                        null,
                        null,
                        MAPPER);

        assertThat(merged.functionCallingEnabled()).isTrue();
        assertThat(merged.functionCallingBackendProposalEnabled()).isTrue();
        assertThat(merged.toolsEnabled()).isTrue();
    }

    @Test
    void catalogRuntimeMapKeys_areAllowedOrPromptOverride() {
        for (RagExperimentalPresetCode code : RagExperimentalPresetCode.values()) {
            Map<String, Object> catalog = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(code);
            LinkedHashMap<String, Object> combined = new LinkedHashMap<>(catalog);
            combined.putAll(ChatProductPresetAlignment.effectiveProductRuntimeValues(code));
            List<String> dropped = RagConfigValueSanitizer.droppedKeys(combined);
            assertThat(dropped)
                    .as("Unexpected dropped keys for preset %s: %s", code, dropped)
                    .doesNotContain(
                            "clarificationEnabled",
                            "memoryEnabled",
                            "adaptiveRoutingEnabled",
                            "judgeEnabled",
                            "deterministicToolRoutingEnabled",
                            "functionCallingBackendProposalEnabled",
                            "functionCallingNativeProviderEnabled",
                            "advancedRetrievalMaxContextChars");
        }
    }
}
