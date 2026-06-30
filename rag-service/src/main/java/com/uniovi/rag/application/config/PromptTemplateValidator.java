package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Validates configurable prompt templates before persistence. */
@Component
public class PromptTemplateValidator {

    public void validateOverrides(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> overrides = PromptOverrideKeys.extractOverrides(values);
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            try {
                ConfigurablePromptGroup group = ConfigurablePromptGroup.fromId(e.getKey());
                validateContent(group, e.getValue());
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        Object systemPrompt = values.get("llmSystemPrompt");
        if (systemPrompt instanceof String text && text.length() > 50_000) {
            errors.add("llmSystemPrompt exceeds maximum length");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public void validateContent(ConfigurablePromptGroup group, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (content.length() > 50_000) {
            throw new IllegalArgumentException(
                    "Prompt override for " + group.id() + " exceeds maximum length");
        }
        List<String> missing = new ArrayList<>();
        for (String variable : group.requiredVariables()) {
            if (!content.contains(variable)) {
                missing.add(variable);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prompt override for "
                            + group.id()
                            + " is missing required variables: "
                            + String.join(", ", missing));
        }
    }
}
