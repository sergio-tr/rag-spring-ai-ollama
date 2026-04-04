package com.uniovi.rag.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises domain enums so static initializer / value space is covered by JaCoCo.
 */
class DomainEnumsCoverageTest {

    @Test
    void evaluationRunType_values_roundTrip() {
        for (EvaluationRunType v : EvaluationRunType.values()) {
            assertEquals(v, EvaluationRunType.valueOf(v.name()));
        }
    }

    @Test
    void evaluationDatasetType_values_roundTrip() {
        for (EvaluationDatasetType v : EvaluationDatasetType.values()) {
            assertEquals(v, EvaluationDatasetType.valueOf(v.name()));
        }
    }

    @Test
    void projectDocumentStatus_values_roundTrip() {
        for (ProjectDocumentStatus v : ProjectDocumentStatus.values()) {
            assertEquals(v, ProjectDocumentStatus.valueOf(v.name()));
        }
    }

    @Test
    void classifierModelStatus_values_roundTrip() {
        for (ClassifierModelStatus v : ClassifierModelStatus.values()) {
            assertEquals(v, ClassifierModelStatus.valueOf(v.name()));
        }
    }

    @Test
    void evaluationRunStatus_values_roundTrip() {
        for (EvaluationRunStatus v : EvaluationRunStatus.values()) {
            assertEquals(v, EvaluationRunStatus.valueOf(v.name()));
        }
    }
}
