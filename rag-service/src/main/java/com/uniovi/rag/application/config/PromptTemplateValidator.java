package com.uniovi.rag.application.config;

import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates configurable prompt templates before persistence. */
@Component
public class PromptTemplateValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    public void validateOverrides(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> overrides = PromptOverrideKeys.extractOverrides(values);
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            ConfigurablePromptGroup group = ConfigurablePromptGroup.fromId(e.getKey());
            validateContent(group, e.getValue());
        }
        Object systemPrompt = values.get("llmSystemPrompt");
        if (systemPrompt instanceof String text && text.length() > 50_000) {
            throw invalid(
                    null,
                    "llmSystemPrompt",
                    "llmSystemPrompt exceeds maximum length",
                    null,
                    List.of());
        }
        validateSimilarityThreshold(values);
    }

    public void validateContent(ConfigurablePromptGroup group, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (content.length() > 50_000) {
            throw invalid(
                    group.id(),
                    promptFieldFor(group),
                    "Prompt override for " + group.id() + " exceeds maximum length",
                    null,
                    allowedPlaceholders(group));
        }
        List<String> missing = new ArrayList<>();
        for (String variable : group.requiredVariables()) {
            if (!content.contains(variable)) {
                missing.add(variable);
            }
        }
        if (!missing.isEmpty()) {
            throw invalid(
                    group.id(),
                    promptFieldFor(group),
                    "Prompt override for "
                            + group.id()
                            + " is missing required variables: "
                            + String.join(", ", missing),
                    null,
                    allowedPlaceholders(group));
        }
        validatePlaceholders(group, content);
    }

    private static void validatePlaceholders(ConfigurablePromptGroup group, String content) {
        Set<String> allowed = new LinkedHashSet<>(allowedPlaceholders(group));
        Matcher matcher = PLACEHOLDER.matcher(content);
        while (matcher.find()) {
            String token = "{" + matcher.group(1) + "}";
            if (!allowed.contains(token)) {
                throw invalid(
                        group.id(),
                        promptFieldFor(group),
                        "Prompt override for " + group.id() + " contains invalid placeholder: " + token,
                        token,
                        List.copyOf(allowed));
            }
        }
    }

    private static List<String> allowedPlaceholders(ConfigurablePromptGroup group) {
        List<String> allowed = new ArrayList<>();
        allowed.addAll(group.requiredVariables());
        allowed.addAll(group.optionalVariables());
        return List.copyOf(allowed);
    }

    private static String promptFieldFor(ConfigurablePromptGroup group) {
        return PromptOverrideKeys.OVERRIDES_MAP_KEY + "." + group.id();
    }

    private static void validateSimilarityThreshold(Map<String, Object> values) {
        if (values == null || !values.containsKey("similarityThreshold")) {
            return;
        }
        Object raw = values.get("similarityThreshold");
        if (raw == null) {
            return;
        }
        double value;
        try {
            value = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            throw invalid(
                    null,
                    "similarityThreshold",
                    "similarityThreshold must be a number between 0 and 1",
                    null,
                    List.of());
        }
        if (value < 0.0 || value > 1.0) {
            throw invalid(
                    null,
                    "similarityThreshold",
                    "similarityThreshold must be between 0 and 1",
                    null,
                    List.of());
        }
    }

    private static PromptTemplateValidationException invalid(
            String promptGroup,
            String field,
            String message,
            String invalidPlaceholder,
            List<String> allowedPlaceholders) {
        return new PromptTemplateValidationException(
                promptGroup, field, message, invalidPlaceholder, allowedPlaceholders);
    }
}
