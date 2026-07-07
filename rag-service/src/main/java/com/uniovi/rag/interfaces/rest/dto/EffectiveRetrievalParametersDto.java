package com.uniovi.rag.interfaces.rest.dto;

/**
 * Effective retrieval parameters for Chat with provenance.
 *
 * @param topKSource one of {@code USER_DEFAULTS}, {@code PRESET_LOCKED}, {@code CONVERSATION_CUSTOM}
 * @param similarityThresholdSource same vocabulary as {@code topKSource}
 */
public record EffectiveRetrievalParametersDto(
        int topK,
        double similarityThreshold,
        String topKSource,
        String similarityThresholdSource) {}
