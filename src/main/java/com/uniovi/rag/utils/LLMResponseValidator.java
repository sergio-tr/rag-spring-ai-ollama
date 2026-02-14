package com.uniovi.rag.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for validating LLM responses consistently across all services.
 * This helps ensure response quality and handle edge cases uniformly.
 */
public class LLMResponseValidator {
    
    private static final Logger log = LoggerFactory.getLogger(LLMResponseValidator.class);
    
    /** Minimum length (e.g. "No.", "Si." = 3); short valid answers like "4 actas" are accepted. */
    private static final int MIN_RESPONSE_LENGTH = 3;
    private static final int MAX_RESPONSE_LENGTH = 10000;
    
    /**
     * Validates if an LLM response is valid and usable.
     * 
     * @param response The response to validate
     * @param context Optional context for logging (e.g., "NER", "Expander", "Tool")
     * @return true if the response is valid, false otherwise
     */
    public static boolean isValidResponse(String response, String context) {
        if (response == null) {
            log.warn("{}: Response is null", context);
            return false;
        }
        
        String trimmed = response.trim();
        
        if (trimmed.isEmpty()) {
            log.warn("{}: Response is empty", context);
            return false;
        }
        
        // Allow short valid answers: "Si.", "No.", "4 actas", numeric answers
        if (trimmed.length() < MIN_RESPONSE_LENGTH) {
            log.warn("{}: Response too short (length: {}, min {})", context, trimmed.length(), MIN_RESPONSE_LENGTH);
            return false;
        }
        
        if (trimmed.length() > MAX_RESPONSE_LENGTH) {
            log.warn("{}: Response too long (length: {}), may be truncated or corrupted", context, trimmed.length());
            return false;
        }
        
        // Check for common error patterns
        if (isErrorResponse(trimmed)) {
            log.warn("{}: Response appears to be an error message: {}", context, trimmed.substring(0, Math.min(50, trimmed.length())));
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if a response appears to be an error message rather than actual content.
     * Excludes negations (e.g. "no error", "ningun error") and requires clearly technical error context
     * so valid answers like "No se encontro ningun error en el acta" are not rejected.
     *
     * @param response The response to check
     * @return true if it looks like an error message
     */
    private static boolean isErrorResponse(String response) {
        if (response == null || response.isEmpty()) return false;
        String lower = response.toLowerCase().trim();

        // Explicit negation patterns: do not treat as error
        if (lower.matches("(?s).*\\b(no|ningún|ningun|sin)\\s+(ningún|ningun)?\\s*error\\b.*")) return false;
        if (lower.matches("(?s).*\\bno\\s+hay\\s+error.*")) return false;
        if (lower.matches("(?s).*\\bno\\s+se\\s+encontr[oó]\\s+(ningún|ningun)?\\s*error.*")) return false;

        // Starts with error/exception prefix (technical)
        if (lower.startsWith("error:") || lower.startsWith("exception:")) return true;

        // Clearly technical error phrases (not just the word "error" in isolation)
        if (lower.contains("error occurred") || lower.contains("an error occurred")) return true;
        if (lower.contains("processing error") || lower.contains("processing exception")) return true;
        if (lower.contains("exception") && (lower.contains("thrown") || lower.contains("caught") || lower.contains("at "))) return true;
        if (lower.contains("failed to process") || lower.contains("failed to retrieve")) return true;

        return false;
    }
    
    /**
     * Cleans and normalizes an LLM response.
     * Removes common artifacts like markdown code blocks, extra whitespace, etc.
     * 
     * @param response The response to clean
     * @return The cleaned response
     */
    public static String cleanResponse(String response) {
        if (response == null) {
            return "";
        }
        
        String cleaned = response
                .replaceAll("(?s)```.*?```", "")  // Remove markdown code blocks
                .replaceAll("(?s)```.*?\\n", "")     // Remove incomplete code blocks
                .replaceAll("```", "")                // Remove any remaining ```
                .replaceAll("(?m)^\\s*//.*$", "")   // Remove comments
                .replaceAll("(?m)^\\s*#.*$", "")     // Remove markdown headers
                .trim();
        
        // Remove leading/trailing quotes if present
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        
        return cleaned.trim();
    }
    
    /**
     * Validates and cleans a response in one step.
     * 
     * @param response The response to validate and clean
     * @param context Optional context for logging
     * @return The cleaned response if valid, null otherwise
     */
    public static String validateAndClean(String response, String context) {
        if (!isValidResponse(response, context)) {
            return null;
        }
        
        String cleaned = cleanResponse(response);
        
        // Re-validate after cleaning
        if (!isValidResponse(cleaned, context + " (cleaned)")) {
            return null;
        }
        
        return cleaned;
    }
}

