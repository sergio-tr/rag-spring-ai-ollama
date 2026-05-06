package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentalDatasetResolverTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationDatasetStorePort evaluationDatasetStorePort;

    @Mock
    private EvaluationWorkbookParser evaluationWorkbookParser;

    @Test
    void resolve_returnsLlmQuestions_whenRunBenchmarkMatchesDataset() throws Exception {
        UUID runId = UUID.randomUUID();
        EvaluationDatasetEntity ds = EvaluationDatasetEntity.newLabUploadPlaceholder();
        ds.setExperimentalKind(ExperimentalDatasetType.LLM_MODEL_BASELINE.name());
        ds.setStorageUri("datasets/u1/file.xlsx");

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
        run.setDataset(ds);

        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        LlmReaderQuestion q =
                new LlmReaderQuestion(
                        "qid-1",
                        "Question?",
                        "",
                        "Answer",
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        "",
                        "",
                        false,
                        "");
        List<LlmReaderQuestion> qs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            qs.add(new LlmReaderQuestion(
                    "qid-" + i,
                    "Question " + i + "?",
                    "",
                    "Answer",
                    Optional.empty(),
                    Optional.empty(),
                    "",
                    "",
                    "",
                    false,
                    ""));
        }
        EvaluationWorkbook wb =
                EvaluationWorkbook.builder().sheetNamesPresent(List.of()).llmReaderQuestions(qs).build();
        when(evaluationDatasetStorePort.openStream("datasets/u1/file.xlsx"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(evaluationWorkbookParser.parse(any(InputStream.class), eq(ExperimentalDatasetType.LLM_MODEL_BASELINE)))
                .thenReturn(new WorkbookParseResult(wb, new ValidationReport()));

        ExperimentalDatasetResolver resolver =
                new ExperimentalDatasetResolver(evaluationRunRepository, evaluationDatasetStorePort, evaluationWorkbookParser);

        TypedBenchmarkDataset out = resolver.resolve(runId);
        assertThat(out).isInstanceOf(TypedBenchmarkDataset.LlmQuestions.class);
        assertThat(((TypedBenchmarkDataset.LlmQuestions) out).questions()).containsExactlyElementsOf(qs);
    }

    @Test
    void resolve_throwsWhenBenchmarkKindMismatchesDatasetRows() {
        UUID runId = UUID.randomUUID();
        EvaluationDatasetEntity ds = EvaluationDatasetEntity.newLabUploadPlaceholder();
        ds.setExperimentalKind(ExperimentalDatasetType.LLM_MODEL_BASELINE.name());
        ds.setStorageUri("datasets/u1/file.xlsx");

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        run.setDataset(ds);

        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        ExperimentalDatasetResolver resolver =
                new ExperimentalDatasetResolver(evaluationRunRepository, evaluationDatasetStorePort, evaluationWorkbookParser);

        assertThatThrownBy(() -> resolver.resolve(runId)).isInstanceOf(BenchmarkDatasetResolutionException.class);
    }

    @Test
    void resolve_referenceBundle_resolvesExpectedMinimumRowCounts_perBenchmarkKind() {
        ExperimentalDatasetResolver resolver =
                new ExperimentalDatasetResolver(evaluationRunRepository, evaluationDatasetStorePort, new EvaluationWorkbookParser());

        EvaluationDatasetEntity ds = EvaluationDatasetEntity.newLabUploadPlaceholder();
        ds.setExperimentalKind(ExperimentalDatasetType.REFERENCE_BUNDLE.name());
        ds.setStorageUri("classpath:" + EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);

        // LLM_JUDGE_QA → llm_reader_questions
        {
            UUID runId = UUID.randomUUID();
            EvaluationRunEntity run = new EvaluationRunEntity();
            run.setId(runId);
            run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
            run.setDataset(ds);
            when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));
            TypedBenchmarkDataset out = resolver.resolve(runId);
            assertThat(out).isInstanceOf(TypedBenchmarkDataset.LlmQuestions.class);
            assertThat(((TypedBenchmarkDataset.LlmQuestions) out).questions()).hasSize(36);
        }

        // EMBEDDING_RETRIEVAL → embedding_retrieval_queries
        {
            UUID runId = UUID.randomUUID();
            EvaluationRunEntity run = new EvaluationRunEntity();
            run.setId(runId);
            run.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
            run.setDataset(ds);
            when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));
            TypedBenchmarkDataset out = resolver.resolve(runId);
            assertThat(out).isInstanceOf(TypedBenchmarkDataset.EmbeddingQuestions.class);
            assertThat(((TypedBenchmarkDataset.EmbeddingQuestions) out).dataset().queries()).hasSize(60);
        }

        // RAG_PRESET_END_TO_END → rag_preset_questions_enriched
        {
            UUID runId = UUID.randomUUID();
            EvaluationRunEntity run = new EvaluationRunEntity();
            run.setId(runId);
            run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
            run.setDataset(ds);
            when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));
            TypedBenchmarkDataset out = resolver.resolve(runId);
            assertThat(out).isInstanceOf(TypedBenchmarkDataset.RagPresetQuestions.class);
            assertThat(((TypedBenchmarkDataset.RagPresetQuestions) out).questions()).hasSize(60);
        }

        // Sanity: classpath resource exists (helps debug failures locally).
        assertThat(new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION).exists())
                .isTrue();
    }
}
