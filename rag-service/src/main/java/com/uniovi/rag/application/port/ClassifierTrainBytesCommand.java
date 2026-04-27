package com.uniovi.rag.application.port;

/**
 * Parameters for {@link ClassifierLabPort#trainBytes(ClassifierTrainBytesCommand)} (async train from materialized uploads).
 */
public record ClassifierTrainBytesCommand(
        byte[] fileContent,
        String datasetFilename,
        String modelName,
        String labelsJson,
        byte[] labelsFileContent,
        String labelsFilename,
        int epochs,
        int batchSize) {}
