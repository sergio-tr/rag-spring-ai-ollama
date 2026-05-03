package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabEvaluationRunServiceTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationResultRepository evaluationResultRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private LabEvaluationRunService service;

    @Test
    void compare_matchingRagPresetRuns_returnsComparable() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();

        RagPresetEntity preset = mock(RagPresetEntity.class);
        when(preset.getId()).thenReturn(presetId);

        KnowledgeIndexSnapshotEntity indexSnap = mock(KnowledgeIndexSnapshotEntity.class);
        when(indexSnap.getId()).thenReturn(UUID.randomUUID());

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        stubComparableBaseline(a, b, BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(a.getPreset()).thenReturn(preset);
        when(b.getPreset()).thenReturn(preset);
        when(a.getIndexSnapshot()).thenReturn(indexSnap);
        when(b.getIndexSnapshot()).thenReturn(indexSnap);
        when(a.getIndexSignatureHash()).thenReturn("hash-a");
        when(b.getIndexSignatureHash()).thenReturn("hash-a");

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isTrue();
        assertThat(dto.incompatibilityReasons()).isEmpty();
    }

    @Test
    void compare_benchmarkKindMismatch_listsReason() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        stubComparableBaseline(a, b, BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(a.getBenchmarkKind()).thenReturn(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        when(b.getBenchmarkKind()).thenReturn(BenchmarkKind.EMBEDDING_RETRIEVAL.name());

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isFalse();
        assertThat(dto.incompatibilityReasons()).contains("benchmark_kind mismatch");
    }

    @Test
    void compare_embeddingRetrieval_indexSnapshotMismatch_listsReason() {
        UUID userId = UUID.randomUUID();
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();

        EvaluationRunEntity a = mock(EvaluationRunEntity.class);
        EvaluationRunEntity b = mock(EvaluationRunEntity.class);
        String kind = BenchmarkKind.EMBEDDING_RETRIEVAL.name();
        stubComparableBaseline(a, b, kind);
        when(a.getBenchmarkKind()).thenReturn(kind);
        when(b.getBenchmarkKind()).thenReturn(kind);

        KnowledgeIndexSnapshotEntity ia = mock(KnowledgeIndexSnapshotEntity.class);
        KnowledgeIndexSnapshotEntity ib = mock(KnowledgeIndexSnapshotEntity.class);
        when(ia.getId()).thenReturn(UUID.randomUUID());
        when(ib.getId()).thenReturn(UUID.randomUUID());
        when(a.getIndexSnapshot()).thenReturn(ia);
        when(b.getIndexSnapshot()).thenReturn(ib);
        when(a.getIndexSignatureHash()).thenReturn("same");
        when(b.getIndexSignatureHash()).thenReturn("same");

        when(evaluationRunRepository.findByIdAndUser_Id(runA, userId)).thenReturn(Optional.of(a));
        when(evaluationRunRepository.findByIdAndUser_Id(runB, userId)).thenReturn(Optional.of(b));

        CompareRunsResponseDto dto = service.compare(userId, runA, runB);
        assertThat(dto.comparable()).isFalse();
        assertThat(dto.incompatibilityReasons()).contains("index_snapshot_id mismatch");
    }

    private static void stubComparableBaseline(EvaluationRunEntity a, EvaluationRunEntity b, String benchmarkKind) {
        when(a.getDatasetSha256()).thenReturn("sha");
        when(b.getDatasetSha256()).thenReturn("sha");
        when(a.getRunKind()).thenReturn("rk");
        when(b.getRunKind()).thenReturn("rk");
        when(a.getWorkflowSchemaVersion()).thenReturn("1");
        when(b.getWorkflowSchemaVersion()).thenReturn("1");
        when(a.getBenchmarkKind()).thenReturn(benchmarkKind);
        when(b.getBenchmarkKind()).thenReturn(benchmarkKind);
    }
}
