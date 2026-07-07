package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.application.service.evaluation.metrics.DatasetQuestionSubsetSupport;
import com.uniovi.rag.application.service.evaluation.metrics.RoleEvalCaseSubsetSupport;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
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
import java.util.Map;
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

    @Test
    void resolve_filtersLlmQuestions_whenRunHasDatasetQuestionSubset() throws Exception {
        UUID runId = UUID.randomUUID();
        EvaluationDatasetEntity ds = EvaluationDatasetEntity.newLabUploadPlaceholder();
        ds.setExperimentalKind(ExperimentalDatasetType.LLM_MODEL_BASELINE.name());
        ds.setStorageUri("datasets/u1/file.xlsx");

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
        run.setDataset(ds);
        run.setAggregatesJson(
                Map.of(
                        DatasetQuestionSubsetSupport.AGG_KEY_DATASET_QUESTION_FILTER,
                        DatasetQuestionSubsetSupport.FILTER_EXPLICIT_IDS,
                        DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_IDS,
                        List.of("LLM-001", "LLM-002")));

        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        LlmReaderQuestion q1 = llmQuestion("LLM-001");
        LlmReaderQuestion q2 = llmQuestion("LLM-002");
        List<LlmReaderQuestion> all = new ArrayList<>();
        all.add(q1);
        all.add(q2);
        for (int i = 3; i <= 12; i++) {
            all.add(llmQuestion("LLM-" + String.format("%03d", i)));
        }
        EvaluationWorkbook wb =
                EvaluationWorkbook.builder()
                        .sheetNamesPresent(List.of())
                        .llmReaderQuestions(all)
                        .build();
        when(evaluationDatasetStorePort.openStream("datasets/u1/file.xlsx"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(evaluationWorkbookParser.parse(any(InputStream.class), eq(ExperimentalDatasetType.LLM_MODEL_BASELINE)))
                .thenReturn(new WorkbookParseResult(wb, new ValidationReport()));

        ExperimentalDatasetResolver resolver =
                new ExperimentalDatasetResolver(evaluationRunRepository, evaluationDatasetStorePort, evaluationWorkbookParser);

        TypedBenchmarkDataset out = resolver.resolve(runId);
        assertThat(out).isInstanceOf(TypedBenchmarkDataset.LlmQuestions.class);
        assertThat(((TypedBenchmarkDataset.LlmQuestions) out).questions()).containsExactly(q1, q2);
    }

    @Test
    void resolve_returnsRoleCases_whenRoleEvalModeAndSubsetFilter() throws Exception {
        UUID runId = UUID.randomUUID();
        EvaluationDatasetEntity ds = EvaluationDatasetEntity.newLabUploadPlaceholder();
        ds.setExperimentalKind(ExperimentalDatasetType.LLM_MODEL_BASELINE.name());
        ds.setStorageUri("datasets/u1/file.xlsx");

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(runId);
        run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
        run.setDataset(ds);
        run.setAggregatesJson(
                Map.of(
                        RoleEvalCaseSubsetSupport.AGG_KEY_ROLE_EVAL_MODE,
                        true,
                        BenchmarkRunOrchestrator.AGG_KEY_BENCHMARK_RUNTIME_PARAMETERS,
                        Map.of(
                                RoleEvalCaseSubsetSupport.RUNTIME_ROLE_EVAL_SUBSET,
                                "LLM_JSON_REASONING",
                                RoleEvalCaseSubsetSupport.RUNTIME_ROLE_EVAL_ROLE_PROFILE,
                                "JSON_STRICT"),
                        DatasetQuestionSubsetSupport.AGG_KEY_DATASET_QUESTION_FILTER,
                        DatasetQuestionSubsetSupport.FILTER_EXPLICIT_IDS,
                        DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_IDS,
                        List.of("LLM-JS-001", "LLM-JS-002")));

        when(evaluationRunRepository.findByIdFetchDataset(runId)).thenReturn(Optional.of(run));

        LlmRoleEvalCase js1 =
                new LlmRoleEvalCase(
                        "LLM-JS-001",
                        "LLM_JSON_REASONING",
                        "METADATA_REASONING",
                        "JSON_STRICT",
                        "q1",
                        "",
                        "{}",
                        "",
                        "",
                        "json_schema",
                        "a",
                        "");
        LlmRoleEvalCase js2 =
                new LlmRoleEvalCase(
                        "LLM-JS-002",
                        "LLM_JSON_REASONING",
                        "METADATA_REASONING",
                        "JSON_STRICT",
                        "q2",
                        "",
                        "{}",
                        "",
                        "",
                        "json_schema",
                        "b",
                        "");
        LlmRoleEvalCase rw =
                new LlmRoleEvalCase(
                        "LLM-RW-001",
                        "LLM_REWRITE_EXPANSION",
                        "QUERY_REWRITE",
                        "REWRITE_BALANCED",
                        "q",
                        "",
                        "out",
                        "",
                        "",
                        "normalized_match",
                        "",
                        "");
        List<LlmReaderQuestion> qa = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            qa.add(llmQuestion("LLM-" + String.format("%03d", i + 1)));
        }
        EvaluationWorkbook wb =
                EvaluationWorkbook.builder()
                        .sheetNamesPresent(List.of())
                        .llmReaderQuestions(qa)
                        .llmRoleEvalCases(List.of(js1, js2, rw))
                        .build();
        when(evaluationDatasetStorePort.openStream("datasets/u1/file.xlsx"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(evaluationWorkbookParser.parse(any(InputStream.class), eq(ExperimentalDatasetType.LLM_MODEL_BASELINE)))
                .thenReturn(new WorkbookParseResult(wb, new ValidationReport()));

        ExperimentalDatasetResolver resolver =
                new ExperimentalDatasetResolver(evaluationRunRepository, evaluationDatasetStorePort, evaluationWorkbookParser);

        TypedBenchmarkDataset out = resolver.resolve(runId);
        assertThat(out).isInstanceOf(TypedBenchmarkDataset.LlmRoleCases.class);
        assertThat(((TypedBenchmarkDataset.LlmRoleCases) out).cases()).containsExactly(js1, js2);
    }

    private static LlmReaderQuestion llmQuestion(String id) {
        return new LlmReaderQuestion(
                id,
                "Question for " + id,
                "context",
                "Answer",
                Optional.empty(),
                Optional.empty(),
                "",
                "",
                "",
                false,
                "");
    }
}
