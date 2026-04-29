package com.uniovi.rag.service.query;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and cleans LLM responses consistently across the query pipeline.
 */
public class LLMResponseValidatorService implements ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(LLMResponseValidatorService.class);
    private static final int MIN_RESPONSE_LENGTH = 2;
    private static final int MAX_RESPONSE_LENGTH = 10000;

    @Override
    public boolean isValidResponse(String response, String context) {
        if (response == null) {
            log.warn("{}: Response is null", context);
            return false;
        }
        String trimmed = response.trim();
        if (trimmed.isEmpty()) {
            log.warn("{}: Response is empty", context);
            return false;
        }
        if (trimmed.length() < MIN_RESPONSE_LENGTH) {
            log.warn("{}: Response too short (length: {}, min {})", context, trimmed.length(), MIN_RESPONSE_LENGTH);
            return false;
        }
        if (trimmed.length() > MAX_RESPONSE_LENGTH) {
            log.warn("{}: Response too long (length: {}), may be truncated or corrupted", context, trimmed.length());
            return false;
        }
        if (isErrorResponse(trimmed)) {
            log.warn("{}: Response appears to be an error message: {}", context, trimmed.substring(0, Math.min(50, trimmed.length())));
            return false;
        }
        return true;
    }

    @Override
    public String cleanResponse(String response) {
        if (response == null) return "";
        String cleaned = response
                .replaceAll("(?s)```.*?```", "")
                .replaceAll("```[^\\n]*+\\n", "")
                .replace("```", "")
                .replaceAll("(?m)^\\s*//.*$", "")
                .replaceAll("(?m)^\\s*#.*$", "")
                .trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    @Override
    public String validateAndClean(String response, String context) {
        if (!isValidResponse(response, context)) return null;
        String cleaned = cleanResponse(response);
        if (!isValidResponse(cleaned, context + " (cleaned)")) return null;
        return cleaned;
    }

    private static boolean isErrorResponse(String response) {
        if (response == null || response.isEmpty()) return false;
        String lower = response.toLowerCase(Locale.ROOT).trim();
        if (isExplicitNoErrorPhrase(lower)) return false;
        return (lower.startsWith("error:") || lower.startsWith("exception:"))
                || lower.contains("error occurred")
                || lower.contains("an error occurred")
                || lower.contains("processing error")
                || lower.contains("processing exception")
                || looksLikeExceptionTrace(lower)
                || lower.contains("failed to process")
                || lower.contains("failed to retrieve");
    }

    private static boolean looksLikeExceptionTrace(String lower) {
        return lower.contains("exception")
                && (lower.contains("thrown") || lower.contains("caught") || lower.contains("at "));
    }

    private static boolean isExplicitNoErrorPhrase(String lower) {
        // Avoid regex here: simple phrase checks are sufficient and safer.
        // Normalize common Spanish variants without attempting full NLP.
        if (lower.contains("no hay error")) return true;
        if (lower.contains("no se encontro error")) return true;
        if (lower.contains("no se encontró error")) return true;

        // "no error", "sin error", "ningun error", "ningún error"
        if (lower.contains(" no error")) return true;
        if (lower.contains(" sin error")) return true;
        if (lower.contains(" ningun error")) return true;
        if (lower.contains(" ningún error")) return true;
        return false;
    }
}
