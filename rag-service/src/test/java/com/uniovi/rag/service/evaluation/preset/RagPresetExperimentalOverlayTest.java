package com.uniovi.rag.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPresetExperimentalOverlayTest {

    @Test
    void p0_direct_llm_disables_retrieval_in_features_and_terminal_json() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        base.setUseRetrieval(true);
        base.setToolsEnabled(true);

        RagPresetExperimentalOverlay.Overlay o =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P0);

        assertThat(o.features().isUseRetrieval()).isFalse();
        assertThat(o.features().isToolsEnabled()).isFalse();
        ObjectNode j = o.terminalRuntimeJson();
        assertThat(j.get("useRetrieval").asBoolean()).isFalse();
        assertThat(j.get("naiveFullCorpusInPromptEnabled").asBoolean()).isFalse();
    }

    @Test
    void p1_enables_naive_full_corpus_in_terminal_json() {
        RagPresetExperimentalOverlay.Overlay o =
                RagPresetExperimentalOverlay.build(new RagFeatureConfiguration(), RagExperimentalPresetCode.P1);
        ObjectNode j = o.terminalRuntimeJson();
        assertThat(j.get("useRetrieval").asBoolean()).isTrue();
        assertThat(j.get("naiveFullCorpusInPromptEnabled").asBoolean()).isTrue();
    }

    @Test
    void p14_enables_judge_flags() {
        RagPresetExperimentalOverlay.Overlay o =
                RagPresetExperimentalOverlay.build(new RagFeatureConfiguration(), RagExperimentalPresetCode.P14);
        assertThat(o.features().isJudgeEnabled()).isTrue();
        assertThat(o.terminalRuntimeJson().get("judgeEnabled").asBoolean()).isTrue();
    }
}
