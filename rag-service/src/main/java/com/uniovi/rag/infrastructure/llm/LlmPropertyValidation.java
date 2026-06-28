package com.uniovi.rag.infrastructure.llm;

/**
 * Shared validation helpers for {@link LlmProperties} nested defaults.
 */
final class LlmPropertyValidation {

    static final double TEMPERATURE_MIN = 0.0;
    static final double TEMPERATURE_MAX = 2.0;

    private LlmPropertyValidation() {}

    static void requirePositiveTimeout(long timeoutMs, String propertyKey) {
        if (timeoutMs <= 0) {
            throw new IllegalStateException(propertyKey + " must be positive");
        }
    }

    static void requireReasonableTemperature(double temperature, String propertyKey) {
        if (Double.isNaN(temperature) || temperature < TEMPERATURE_MIN || temperature > TEMPERATURE_MAX) {
            throw new IllegalStateException(
                    propertyKey + " must be between " + TEMPERATURE_MIN + " and " + TEMPERATURE_MAX);
        }
    }
}
