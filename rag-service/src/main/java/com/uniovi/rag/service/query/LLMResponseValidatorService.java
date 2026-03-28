package com.uniovi.rag.service.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Validates and cleans LLM responses consistently across the query pipeline.
 */
public class LLMResponseValidatorService implements ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(LLMResponseValidatorService.class);
    private static final int MIN_RESPONSE_LENGTH = 2;
    private static final int MAX_RESPONSE_LENGTH = 10000;
    private static final int RX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.CANON_EQ;
    private static final Pattern NO_ERROR_PHRASE = Pattern.compile(
            ".*\\b(no|ningún|ningun|sin)\\s+(ningún|ningun)?\\s*error\\b.*", RX_FLAGS);
    private static final Pattern NO_HAY_ERROR = Pattern.compile(".*\\bno\\s+hay\\s+error.*", RX_FLAGS);
    private static final Pattern NO_ERROR_ENCONTRADO = Pattern.compile(
            ".*\\bno\\s+se\\s+encontr[oó]\\s+(ningún|ningun)?\\s*error.*", RX_FLAGS);

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
                .replaceAll("(?s)```.*?\\n", "")
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
        String lower = response.toLowerCase().trim();
        if (NO_ERROR_PHRASE.matcher(lower).matches()) return false;
        if (NO_HAY_ERROR.matcher(lower).matches()) return false;
        if (NO_ERROR_ENCONTRADO.matcher(lower).matches()) return false;
        if (lower.startsWith("error:") || lower.startsWith("exception:")) return true;
        if (lower.contains("error occurred") || lower.contains("an error occurred")) return true;
        if (lower.contains("processing error") || lower.contains("processing exception")) return true;
        if (lower.contains("exception") && (lower.contains("thrown") || lower.contains("caught") || lower.contains("at "))) return true;
        if (lower.contains("failed to process") || lower.contains("failed to retrieve")) return true;
        return false;
    }
}
