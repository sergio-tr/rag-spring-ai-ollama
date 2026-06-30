package com.uniovi.rag.testsupport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPromptResolver;
import com.uniovi.rag.application.port.PresetProfileCompositionSources;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import java.util.Map;
import java.util.Optional;

/** Test double returning platform defaults (no persisted overrides). */
public final class TestConfigurablePromptResolver {

    private TestConfigurablePromptResolver() {}

    public static ConfigurablePromptResolver defaultsOnly() {
        ConfigurationSourcePort empty =
                new ConfigurationSourcePort() {
                    @Override
                    public Optional<java.util.Map<String, Object>> loadSystemDefaults() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.util.Map<String, Object>> loadUserDefault(java.util.UUID userId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.util.Map<String, Object>> loadProject(
                            java.util.UUID userId, java.util.UUID projectId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PresetProfileCompositionSources> loadPresetProfileCompositionSources(
                            java.util.UUID userId, java.util.UUID presetId) {
                        return Optional.empty();
                    }
                };
        return new ConfigurablePromptResolver(empty, new ObjectMapper());
    }

    public static ConfigurablePromptResolver withOverrides(Map<String, Object> projectValues) {
        ConfigurationSourcePort source =
                new ConfigurationSourcePort() {
                    @Override
                    public Optional<java.util.Map<String, Object>> loadSystemDefaults() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.util.Map<String, Object>> loadUserDefault(java.util.UUID userId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<java.util.Map<String, Object>> loadProject(
                            java.util.UUID userId, java.util.UUID projectId) {
                        return Optional.of(projectValues);
                    }

                    @Override
                    public Optional<PresetProfileCompositionSources> loadPresetProfileCompositionSources(
                            java.util.UUID userId, java.util.UUID presetId) {
                        return Optional.empty();
                    }
                };
        return new ConfigurablePromptResolver(source, new ObjectMapper());
    }

    public static RuntimeAnswerPromptResolver answerPromptResolver() {
        return new RuntimeAnswerPromptResolver(defaultsOnly());
    }
}
