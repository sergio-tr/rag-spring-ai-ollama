package com.uniovi.rag.application.service.runtime.validation;

/**
 * Validates and optionally cleans LLM responses for consistency and quality.
 */
public interface ResponseValidator {

    /**
     * Validates if an LLM response is valid and usable.
     *
     * @param response The response to validate
     * @param context  Optional context for logging (e.g. "NER", "Expander", "Tool")
     * @return true if the response is valid, false otherwise
     */
    boolean isValidResponse(String response, String context);

    /**
     * Cleans and normalizes an LLM response (e.g. remove markdown blocks, trim).
     *
     * @param response The response to clean
     * @return The cleaned response
     */
    String cleanResponse(String response);

    /**
     * Validates and cleans a response in one step.
     *
     * @param response The response to validate and clean
     * @param context  Optional context for logging
     * @return The cleaned response if valid, null otherwise
     */
    String validateAndClean(String response, String context);
}
