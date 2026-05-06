package com.uniovi.rag.interfaces.rest.dto.experimental;

/**
 * Counts of typed rows available for each Lab benchmark family.
 *
 * <p>These fields are intentionally explicit (no maps) so the webapp can render a stable dataset table for the TFG.
 */
public record ExperimentalDatasetQuestionCountsDto(
        int llmReaderQuestions,
        int embeddingQueries,
        int ragPresetQuestions,
        int presetCatalog,
        int chunkRegistry) {}

