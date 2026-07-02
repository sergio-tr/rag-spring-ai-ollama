package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DefaultTopKResolutionTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    private ConfigResolver resolver;

    @BeforeEach
    void setUp() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        RagReasoningProperties reasoningProperties = new RagReasoningProperties();
        reasoningProperties.setStrategy("SIMPLE");
        resolver =
                new ConfigResolver(
                        featureConfig,
                        reasoningProperties,
                        configurationSource,
                        new ObjectMapper(),
                        new LlmProperties(),
                        8,
                        0.25);
    }

    @Test
    void deploymentFallbackTopK_isEight() {
        RagConfig out = resolver.resolve(null, null, null);
        assertEquals(8, out.topK());
        assertEquals(0.25, out.similarityThreshold());
    }
}
