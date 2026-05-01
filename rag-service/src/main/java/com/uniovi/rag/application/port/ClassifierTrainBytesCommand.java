package com.uniovi.rag.application.port;

import java.util.Arrays;
import java.util.Objects;

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
        int batchSize) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassifierTrainBytesCommand that)) return false;
        return epochs == that.epochs
                && batchSize == that.batchSize
                && Arrays.equals(fileContent, that.fileContent)
                && Objects.equals(datasetFilename, that.datasetFilename)
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(labelsJson, that.labelsJson)
                && Arrays.equals(labelsFileContent, that.labelsFileContent)
                && Objects.equals(labelsFilename, that.labelsFilename);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(datasetFilename, modelName, labelsJson, labelsFilename, epochs, batchSize);
        result = 31 * result + Arrays.hashCode(fileContent);
        result = 31 * result + Arrays.hashCode(labelsFileContent);
        return result;
    }

    @Override
    public String toString() {
        return "ClassifierTrainBytesCommand["
                + "fileContent(len=" + (fileContent == null ? 0 : fileContent.length)
                + ", hash=" + Arrays.hashCode(fileContent) + ")"
                + ", datasetFilename=" + datasetFilename
                + ", modelName=" + modelName
                + ", labelsJson(len=" + (labelsJson == null ? 0 : labelsJson.length()) + ")"
                + ", labelsFileContent(len=" + (labelsFileContent == null ? 0 : labelsFileContent.length)
                + ", hash=" + Arrays.hashCode(labelsFileContent) + ")"
                + ", labelsFilename=" + labelsFilename
                + ", epochs=" + epochs
                + ", batchSize=" + batchSize
                + "]";
    }
}
