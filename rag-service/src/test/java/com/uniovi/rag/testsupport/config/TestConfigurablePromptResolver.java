package com.uniovi.rag.testsupport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPromptResolver;
import com.uniovi.rag.application.port.PresetProfileCompositionSources;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test double returning platform defaults (no persisted overrides). */
public final class TestConfigurablePromptResolver {

    private TestConfigurablePromptResolver() {}

    public static ConfigurablePromptResolver defaultsOnly() {
        ConfigurationSourcePort empty =
                new ConfigurationSourcePort() {
                    @Override
                    public Optional<Map<String, Object>> loadSystemDefaults() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Map<String, Object>> loadUserDefault(UUID userId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Map<String, Object>> loadProject(UUID userId, UUID projectId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PresetProfileCompositionSources> loadPresetProfileCompositionSources(
                            UUID userId, UUID presetId) {
                        return Optional.empty();
                    }
                };
        return new ConfigurablePromptResolver(empty, new ObjectMapper());
    }

    public static ConfigurablePromptResolver withOverrides(Map<String, Object> projectValues) {
        ConfigurationSourcePort source =
                new ConfigurationSourcePort() {
                    @Override
                    public Optional<Map<String, Object>> loadSystemDefaults() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Map<String, Object>> loadUserDefault(UUID userId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Map<String, Object>> loadProject(UUID userId, UUID projectId) {
                        return Optional.of(projectValues);
                    }

                    @Override
                    public Optional<PresetProfileCompositionSources> loadPresetProfileCompositionSources(
                            UUID userId, UUID presetId) {
                        return Optional.empty();
                    }
                };
        return new ConfigurablePromptResolver(source, new ObjectMapper());
    }

    public static RuntimeAnswerPromptResolver answerPromptResolver() {
        return new RuntimeAnswerPromptResolver(defaultsOnly());
    }
}
