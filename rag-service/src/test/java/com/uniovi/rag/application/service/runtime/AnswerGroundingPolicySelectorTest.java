package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import org.junit.jupiter.api.Test;

class AnswerGroundingPolicySelectorTest {

    @Test
    void directBaseline_whenRetrievalOff() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(false);
        RagConfig rag = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE");
        assertThat(AnswerGroundingPolicySelector.from(rag)).isEqualTo(AnswerGroundingPolicy.DIRECT_UNGROUNDED_BASELINE);
    }

    @Test
    void corpusGroundedBaseline_whenRetrievalOff_andNaiveFullCorpusEnabled() throws Exception {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(false);
        RagConfig rag =
                RagConfig.applyJsonOverrides(
                        RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE"),
                        new ObjectMapper().readTree("{\"naiveFullCorpusInPromptEnabled\": true}"));
        assertThat(AnswerGroundingPolicySelector.from(rag)).isEqualTo(AnswerGroundingPolicy.CORPUS_GROUNDED_BASELINE);
    }

    @Test
    void attemptWithContext_whenRetrievalOn_andLowStrictness() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(true);
        RagConfig rag = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE");
        assertThat(AnswerGroundingPolicySelector.from(rag)).isEqualTo(AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT);
    }

    @Test
    void strict_whenJudgeAndMetadata() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(true);
        fc.setMetadataEnabled(true);
        fc.setJudgeEnabled(true);
        RagConfig rag = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE");
        assertThat(AnswerGroundingPolicySelector.from(rag)).isEqualTo(AnswerGroundingPolicy.STRICT_GROUNDED);
    }

    @Test
    void negative_whenPostRetrievalWithoutStrictStack() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(true);
        fc.setPostRetrievalEnabled(true);
        RagConfig rag = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE");
        assertThat(AnswerGroundingPolicySelector.from(rag)).isEqualTo(AnswerGroundingPolicy.NEGATIVE_GROUNDED);
    }
}
