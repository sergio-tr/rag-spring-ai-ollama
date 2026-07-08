package com.uniovi.rag.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalOverrideModeSupportTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void applyModeAwareRetrieval_assistantDefaultsUsesUserLayer() throws Exception {
        RagConfig base = sampleRagConfig();
        RagConfig presetMerged = withRetrieval(base, 5, 0.9);
        Map<String, Object> user = Map.of("topK", 12, "similarityThreshold", 0.4);
        var override = OM.readTree("{\"retrievalOverrideMode\":\"assistant_defaults\"}");

        RagConfig out =
                RetrievalOverrideModeSupport.applyModeAwareRetrieval(
                        presetMerged, null, override, Map.of(), user, Map.of());

        assertThat(out.topK()).isEqualTo(12);
        assertThat(out.similarityThreshold()).isEqualTo(0.4);
    }

    @Test
    void applyModeAwareRetrieval_projectSettingsUsesProjectValuesWithPerKeyUserFallback() throws Exception {
        RagConfig base = sampleRagConfig();
        RagConfig presetMerged = withRetrieval(base, 5, 0.9);
        Map<String, Object> user = Map.of("topK", 12, "similarityThreshold", 0.4);
        Map<String, Object> project = Map.of("topK", 15);
        var override = OM.readTree("{\"retrievalOverrideMode\":\"project_settings\"}");

        RagConfig out =
                RetrievalOverrideModeSupport.applyModeAwareRetrieval(
                        presetMerged, null, override, Map.of(), user, project);

        assertThat(out.topK()).isEqualTo(15);
        assertThat(out.similarityThreshold()).isEqualTo(0.4);
    }

    @Test
    void applyModeAwareRetrieval_projectSettingsUsesCompleteProjectPair() throws Exception {
        RagConfig base = sampleRagConfig();
        RagConfig presetMerged = withRetrieval(base, 5, 0.9);
        Map<String, Object> user = Map.of("topK", 12, "similarityThreshold", 0.4);
        Map<String, Object> project = Map.of("topK", 15, "similarityThreshold", 0.2);
        var override = OM.readTree("{\"retrievalOverrideMode\":\"project_settings\"}");

        RagConfig out =
                RetrievalOverrideModeSupport.applyModeAwareRetrieval(
                        presetMerged, null, override, Map.of(), user, project);

        assertThat(out.topK()).isEqualTo(15);
        assertThat(out.similarityThreshold()).isEqualTo(0.2);
    }

    @Test
    void applyModeAwareRetrieval_presetModeKeepsMergedValues() throws Exception {
        RagConfig base = sampleRagConfig();
        RagConfig presetMerged = withRetrieval(base, 5, 0.9);
        var override = OM.readTree("{\"retrievalOverrideMode\":\"preset\"}");

        RagConfig out =
                RetrievalOverrideModeSupport.applyModeAwareRetrieval(
                        presetMerged,
                        override,
                        null,
                        Map.of(),
                        Map.of("topK", 12),
                        Map.of("topK", 15));

        assertThat(out.topK()).isEqualTo(5);
        assertThat(out.similarityThreshold()).isEqualTo(0.9);
    }

    @Test
    void applyModeAwareRetrieval_customModeKeepsConversationValues() throws Exception {
        RagConfig base = sampleRagConfig();
        RagConfig customMerged = withRetrieval(base, 7, 0.55);
        var override = OM.readTree("{\"retrievalOverrideMode\":\"custom\",\"topK\":7,\"similarityThreshold\":0.55}");

        RagConfig out =
                RetrievalOverrideModeSupport.applyModeAwareRetrieval(
                        customMerged,
                        null,
                        override,
                        Map.of(),
                        Map.of("topK", 12),
                        Map.of("topK", 15));

        assertThat(out.topK()).isEqualTo(7);
        assertThat(out.similarityThreshold()).isEqualTo(0.55);
    }

    private static RagConfig sampleRagConfig() {
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        features.setUseRetrieval(true);
        return RagConfig.fromFeatureConfiguration(features, 8, 0.25, "lm", "em", null, "NONE");
    }

    private static RagConfig withRetrieval(RagConfig base, int topK, double threshold) {
        return new RagConfig(
                base.expansionEnabled(),
                base.nerEnabled(),
                base.toolsEnabled(),
                base.metadataEnabled(),
                base.reasoningEnabled(),
                base.rankerEnabled(),
                base.postRetrievalEnabled(),
                base.functionCallingEnabled(),
                base.useRetrieval(),
                base.useAdvisor(),
                base.clarificationEnabled(),
                base.memoryEnabled(),
                base.adaptiveRoutingEnabled(),
                base.judgeEnabled(),
                base.deterministicToolRoutingEnabled(),
                topK,
                threshold,
                base.llmModel(),
                base.embeddingModel(),
                base.classifierModelId(),
                base.reasoningStrategy(),
                base.naiveFullCorpusInPromptEnabled(),
                base.naiveFullCorpusMaxChars(),
                base.advancedRetrievalMaxContextChars(),
                base.corpusGroundedDirectWorkflow(),
                base.functionCallingBackendProposalEnabled(),
                base.functionCallingNativeProviderEnabled(),
                base.materializationStrategy());
    }
}
