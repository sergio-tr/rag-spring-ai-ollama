package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import org.springframework.stereotype.Component;

/** Formats metadata-tool prompts from configurable templates. */
@Component
public class MetadataConfigurablePromptFormatter {

    private final ConfigurablePromptResolver promptResolver;

    public MetadataConfigurablePromptFormatter(ConfigurablePromptResolver promptResolver) {
        this.promptResolver = promptResolver;
    }

    public String filterAndListPrompt(String query, String metadataJson) {
        String template =
                ConfigurablePromptRuntimeSupport.resolveFromHolder(
                        promptResolver, ConfigurablePromptGroup.METADATA_FILTER_AND_LIST);
        return String.format(template, query, metadataJson);
    }

    public String booleanQueryPrompt(
            String query,
            String date,
            String place,
            String topics,
            String decisions,
            String summary,
            String agenda) {
        String template =
                ConfigurablePromptRuntimeSupport.resolveFromHolder(
                        promptResolver, ConfigurablePromptGroup.METADATA_BOOLEAN_QUERY);
        return String.format(template, query, date, place, topics, decisions, summary, agenda);
    }

    public String getFieldPrompt(String fieldName, String metadataJson) {
        String template =
                ConfigurablePromptRuntimeSupport.resolveFromHolder(
                        promptResolver, ConfigurablePromptGroup.METADATA_GET_FIELD);
        return String.format(template, fieldName, metadataJson);
    }

    public String summarizeMeetingSystemPrompt() {
        return ConfigurablePromptRuntimeSupport.resolveFromHolder(
                promptResolver, ConfigurablePromptGroup.METADATA_SUMMARIZE_MEETING);
    }
}
