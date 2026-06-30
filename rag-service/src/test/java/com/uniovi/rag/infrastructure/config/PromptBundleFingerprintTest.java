package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.application.service.evaluation.EvaluationJudgePromptSources;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBundleFingerprintTest {

    @Test
    void frozenBundleHash_isStableForSameSources() {
        PromptBundleFingerprint.Result first = PromptBundleFingerprint.computeFrozen();
        PromptBundleFingerprint.Result second = PromptBundleFingerprint.computeFrozen();

        assertThat(first.bundleHashSha256()).isEqualTo(second.bundleHashSha256());
        assertThat(first.bundleVersion()).isEqualTo(PromptBundleFingerprint.BUNDLE_VERSION);
        assertThat(first.includedGroups()).isNotEmpty();
        assertThat(first.excludedGroups()).isNotEmpty();
    }

    @Test
    void evaluationJudgeGroupHash_changesWhenMaterialDiffers() {
        PromptBundleFingerprint.Result baseline = PromptBundleFingerprint.computeFrozen();
        String baselineGroupHash =
                baseline.includedGroups().stream()
                        .filter(g -> PromptBundleFingerprint.GROUP_EVALUATION_JUDGE.equals(g.groupId()))
                        .findFirst()
                        .orElseThrow()
                        .sha256Hex();

        String mutatedHash =
                PromptBundleFingerprint.sha256Hex(
                        EvaluationJudgePromptSources.fingerprintMaterial() + "\n# mutation");

        assertThat(mutatedHash).isNotEqualTo(baselineGroupHash);
    }

    @Test
    void bundleHash_changesWhenRuntimeOverlayAdded() {
        PromptBundleFingerprint.Result frozen = PromptBundleFingerprint.computeFrozen();
        PromptBundleFingerprint.Result withLlm =
                PromptBundleFingerprint.computeForRuntime(SystemPromptLayers.empty(), "", "configured-llm-prompt");

        assertThat(withLlm.bundleHashSha256()).isNotEqualTo(frozen.bundleHashSha256());
    }

    @Test
    void runtimeOverlay_includesSystemLayersAndLlmSystemPrompt() {
        SystemPromptLayers layers =
                new SystemPromptLayers("base", "account", "project", "preset");
        PromptBundleFingerprint.Result result =
                PromptBundleFingerprint.computeForRuntime(layers, "effective prompt", "llm-system");

        assertThat(result.includedGroups())
                .anyMatch(g -> PromptBundleFingerprint.GROUP_SYSTEM_PROMPT_LAYERS.equals(g.groupId()));
        assertThat(result.includedGroups())
                .anyMatch(g -> PromptBundleFingerprint.GROUP_LLM_SYSTEM_PROMPT.equals(g.groupId()));
        assertThat(result.excludedGroups())
                .noneMatch(g -> PromptBundleFingerprint.GROUP_LLM_SYSTEM_PROMPT.equals(g.groupId()));
    }

    @Test
    void evaluationScope_usesSnapshotHashesNotFullText() {
        PromptProfileSnapshot prompts =
                new PromptProfileSnapshot(
                        "eval-baseline-v1",
                        "secret base system",
                        "secret project",
                        "secret chat",
                        "retrieval tpl",
                        "formatting",
                        "effective secret",
                        "abc123");

        PromptBundleFingerprint.Result result =
                PromptBundleFingerprint.computeForEvaluation(prompts, "my-llm-system-prompt");

        Map<String, Object> exportMap = result.toProvenanceMap();
        String serialized = exportMap.toString();
        assertThat(serialized).doesNotContain("secret base system");
        assertThat(serialized).doesNotContain("effective secret");
        assertThat(exportMap).containsKey("promptBundleIncludedGroups");
    }

    @Test
    void toProvenanceMap_listsIncludedAndExcludedGroups() {
        PromptBundleFingerprint.Result result = PromptBundleFingerprint.computeFrozen();
        Map<String, Object> map = result.toProvenanceMap();

        assertThat(map)
                .containsEntry("promptBundleVersion", PromptBundleFingerprint.BUNDLE_VERSION)
                .containsKey("promptBundleSha256")
                .containsKey("promptBundleIncludedGroups")
                .containsKey("promptBundleExcludedGroups");

        @SuppressWarnings("unchecked")
        var included = (java.util.List<Map<String, String>>) map.get("promptBundleIncludedGroups");
        assertThat(included)
                .anyMatch(g -> PromptBundleFingerprint.GROUP_RUNTIME_ANSWER_PROMPTS.equals(g.get("groupId")));
        @SuppressWarnings("unchecked")
        var excluded = (java.util.List<Map<String, String>>) map.get("promptBundleExcludedGroups");
        assertThat(excluded)
                .anyMatch(g -> PromptBundleFingerprint.GROUP_FACTUAL_VERIFIER.equals(g.get("groupId")));
    }
}
