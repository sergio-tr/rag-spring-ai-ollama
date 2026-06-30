package com.uniovi.rag.application.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured prompt template validation failure for API and UI field mapping. */
public final class PromptTemplateValidationException extends RuntimeException {

    public static final String ERROR_CODE = "PROMPT_TEMPLATE_INVALID";

    private final String promptGroup;
    private final String field;
    private final String invalidPlaceholder;
    private final List<String> allowedPlaceholders;

    public PromptTemplateValidationException(
            String promptGroup,
            String field,
            String message,
            String invalidPlaceholder,
            List<String> allowedPlaceholders) {
        super(message);
        this.promptGroup = promptGroup;
        this.field = field;
        this.invalidPlaceholder = invalidPlaceholder;
        this.allowedPlaceholders = allowedPlaceholders != null ? List.copyOf(allowedPlaceholders) : List.of();
    }

    public String promptGroup() {
        return promptGroup;
    }

    public String field() {
        return field;
    }

    public String invalidPlaceholder() {
        return invalidPlaceholder;
    }

    public List<String> allowedPlaceholders() {
        return allowedPlaceholders;
    }

    public Map<String, Object> toDetailsMap() {
        Map<String, Object> details = new LinkedHashMap<>();
        if (promptGroup != null && !promptGroup.isBlank()) {
            details.put("promptGroup", promptGroup);
        }
        if (field != null && !field.isBlank()) {
            details.put("field", field);
        }
        if (invalidPlaceholder != null && !invalidPlaceholder.isBlank()) {
            details.put("invalidPlaceholder", invalidPlaceholder);
        }
        if (!allowedPlaceholders.isEmpty()) {
            details.put("allowedPlaceholders", allowedPlaceholders);
        }
        return Map.copyOf(details);
    }
}
