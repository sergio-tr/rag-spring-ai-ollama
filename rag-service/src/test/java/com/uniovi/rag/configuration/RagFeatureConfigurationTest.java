package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagFeatureConfigurationTest {

    @Test
    void defaults_useRetrievalAndUseAdvisorTrue() {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        assertTrue(c.isUseRetrieval());
        assertTrue(c.isUseAdvisor());
    }

    @Test
    void settersAndGetters() {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        c.setExpansionEnabled(true);
        c.setNerEnabled(true);
        c.setToolsEnabled(true);
        c.setMetadataEnabled(true);
        c.setReasoningEnabled(true);
        c.setRankerEnabled(true);
        c.setPostRetrievalEnabled(true);
        c.setFunctionCallingEnabled(true);
        c.setUseRetrieval(false);
        c.setUseAdvisor(false);

        assertTrue(c.isExpansionEnabled());
        assertTrue(c.isNerEnabled());
        assertTrue(c.isToolsEnabled());
        assertTrue(c.isMetadataEnabled());
        assertTrue(c.isReasoningEnabled());
        assertTrue(c.isRankerEnabled());
        assertTrue(c.isPostRetrievalEnabled());
        assertTrue(c.isFunctionCallingEnabled());
        assertFalse(c.isUseRetrieval());
        assertFalse(c.isUseAdvisor());
    }

    @Test
    void getConfiguration_returnsMapWithAllFlags() {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        c.setToolsEnabled(true);
        Map<String, Boolean> config = c.getConfiguration();
        assertTrue(config.containsKey("expansion"));
        assertTrue(config.containsKey("ner"));
        assertTrue(config.containsKey("tools"));
        assertTrue(config.containsKey("metadata"));
        assertTrue(config.containsKey("reasoning"));
        assertTrue(config.containsKey("ranker"));
        assertTrue(config.containsKey("post-retrieval"));
        assertTrue(config.containsKey("function-calling"));
        assertTrue(config.containsKey("use-retrieval"));
        assertTrue(config.containsKey("use-advisor"));
        assertTrue(config.get("tools"));
        assertTrue(config.get("use-retrieval"));
    }
}
