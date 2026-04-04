package com.uniovi.rag.application.port;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Outbound port: classifier-service lab endpoints (train / evaluate / classify JSON proxy).
 */
public interface ClassifierLabPort {

    Map<String, Object> train(
            MultipartFile file,
            String modelName,
            String labelsJson,
            MultipartFile labelsFile,
            int epochs,
            int batchSize)
            throws IOException;

    Map<String, Object> trainBytes(
            byte[] fileContent,
            String datasetFilename,
            String modelName,
            String labelsJson,
            byte[] labelsFileContent,
            String labelsFilename,
            int epochs,
            int batchSize);

    Map<String, Object> evaluate(String modelId, boolean includeImages, MultipartFile datasetFile) throws IOException;

    Map<String, Object> evaluateBytes(
            String modelId, boolean includeImages, byte[] datasetContent, String datasetFilename);

    Map<String, Object> classify(String query, String modelId);

    boolean isConfigured();
}
