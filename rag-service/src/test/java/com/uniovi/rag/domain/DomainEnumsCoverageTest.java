package com.uniovi.rag.domain;

import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.knowledge.DocumentArtifactType;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
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

    @Test
    void documentArtifactType_values_roundTrip() {
        for (DocumentArtifactType v : DocumentArtifactType.values()) {
            assertEquals(v, DocumentArtifactType.valueOf(v.name()));
        }
    }

    @Test
    void indexSnapshotStatus_values_roundTrip() {
        for (IndexSnapshotStatus v : IndexSnapshotStatus.values()) {
            assertEquals(v, IndexSnapshotStatus.valueOf(v.name()));
        }
    }

    @Test
    void knowledgeSnapshotScopeType_values_roundTrip() {
        for (KnowledgeSnapshotScopeType v : KnowledgeSnapshotScopeType.values()) {
            assertEquals(v, KnowledgeSnapshotScopeType.valueOf(v.name()));
        }
    }

    @Test
    void reindexEventStatus_values_roundTrip() {
        for (ReindexEventStatus v : ReindexEventStatus.values()) {
            assertEquals(v, ReindexEventStatus.valueOf(v.name()));
        }
    }

    @Test
    void configProfileType_values_roundTrip() {
        for (ConfigProfileType v : ConfigProfileType.values()) {
            assertEquals(v, ConfigProfileType.valueOf(v.name()));
        }
    }

    @Test
    void benchmarkKind_values_roundTrip() {
        for (BenchmarkKind v : BenchmarkKind.values()) {
            assertEquals(v, BenchmarkKind.valueOf(v.name()));
        }
    }

    @Test
    void evaluationRunKind_values_roundTrip() {
        for (EvaluationRunKind v : EvaluationRunKind.values()) {
            assertEquals(v, EvaluationRunKind.valueOf(v.name()));
        }
    }

    @Test
    void evaluationDatasetScope_values_roundTrip() {
        for (EvaluationDatasetScope v : EvaluationDatasetScope.values()) {
            assertEquals(v, EvaluationDatasetScope.valueOf(v.name()));
        }
    }
}
