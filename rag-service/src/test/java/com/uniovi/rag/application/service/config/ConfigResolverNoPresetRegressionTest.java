package com.uniovi.rag.application.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression: 3-arg {@code resolve} must match 5-arg path when preset and overrides are absent.
 */
class ConfigResolverNoPresetRegressionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void threeArgMatchesFiveArgWhenNoPresetOrOverrides() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        RagReasoningProperties reasoning = new RagReasoningProperties();
        ConfigurationSourcePort port = mock(ConfigurationSourcePort.class);
        when(port.loadSystemDefaults()).thenReturn(Optional.empty());
        when(port.loadUserDefault(any())).thenReturn(Optional.empty());
        when(port.loadProject(any(), any())).thenReturn(Optional.empty());
        when(port.loadPresetProfileCompositionSources(any(), any())).thenReturn(Optional.empty());

        ConfigResolver resolver =
                new ConfigResolver(featureConfig, reasoning, port, MAPPER, new LlmProperties(), 10, 0.7);

        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        RagConfig three = resolver.resolve(user, project, null);
        RagConfig five = resolver.resolve(user, project, null, null, null);

        assertThat(five).isEqualTo(three);
    }
}
