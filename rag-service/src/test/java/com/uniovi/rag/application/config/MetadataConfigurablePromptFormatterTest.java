package com.uniovi.rag.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import org.junit.jupiter.api.Test;

class MetadataConfigurablePromptFormatterTest {

    private final MetadataConfigurablePromptFormatter formatter =
            new MetadataConfigurablePromptFormatter(TestConfigurablePromptResolver.defaultsOnly());

    @Test
    void filterAndListPrompt_interpolatesQueryAndMetadata() {
        String formatted = formatter.filterAndListPrompt("actas", "{\"items\":[]}");

        assertThat(formatted).contains("actas");
        assertThat(formatted).contains("{\"items\":[]}");
    }

    @Test
    void summarizeMeetingSystemPrompt_returnsPlatformDefault() {
        assertThat(formatter.summarizeMeetingSystemPrompt()).isNotBlank();
    }
}
