package com.uniovi.rag.domain.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalPolicyResolverTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @AfterEach
    void tearDown() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void allowQuestionAnswerAdvisor_falseWhenPostRetrievalEnabled() {
        RagFeatureConfiguration global = new RagFeatureConfiguration();
        global.setUseRetrieval(true);
        global.setUseAdvisor(true);
        global.setPostRetrievalEnabled(true);

        assertFalse(RetrievalPolicyResolver.allowQuestionAnswerAdvisor(global, true, false));
        assertTrue(RetrievalPolicyResolver.allowQuestionAnswerAdvisor(global, true, true));
    }

    @Test
    void allowQuestionAnswerAdvisor_respectsResolvedConfig() throws Exception {
        RagFeatureConfiguration global = new RagFeatureConfiguration();
        global.setPostRetrievalEnabled(false);
        global.setUseAdvisor(false);
        global.setUseRetrieval(true);

        RagConfig base = RagConfig.fromFeatureConfiguration(global, 10, 0.7, "m", "e", "c", "SIMPLE");
        RagConfig resolved = RagConfig.applyJsonOverrides(
                base, OM.readTree("{\"postRetrievalEnabled\": true, \"useAdvisor\": true}"));

        RagExecutionContextHolder.set(RagExecutionContext.forUnscopedExecution(resolved, "t1"));

        assertFalse(RetrievalPolicyResolver.allowQuestionAnswerAdvisor(global, true, false));
    }

    @Test
    void resolvePath_postRetrievalYieldsManual() {
        RagFeatureConfiguration global = new RagFeatureConfiguration();
        global.setUseRetrieval(true);
        global.setPostRetrievalEnabled(true);
        assertEquals(RetrievalPath.MANUAL_ONLY, RetrievalPolicyResolver.resolvePath(global));
    }
}
