package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.LabDatasetGateValidator;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.application.service.evaluation.metrics.DatasetQuestionSubsetSupport;
import com.uniovi.rag.application.service.evaluation.metrics.RoleEvalCaseSubsetSupport;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalDataset;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.LlmRoleEvalCase;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4 bridge: {@code evaluation_run.id} → dataset row → bytes → {@link com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook} → typed lists for handlers.
 *
 * <p>Resolves typed benchmark datasets only (no Map-based Q/A classpath).
 */
@Service
public class ExperimentalDatasetResolver {

    static final String CLASSPATH_STORAGE_PREFIX = "classpath:";

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser;

    public ExperimentalDatasetResolver(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationDatasetStorePort evaluationDatasetStorePort,
            EvaluationWorkbookParser evaluationWorkbookParser) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationDatasetStorePort = evaluationDatasetStorePort;
        this.evaluationWorkbookParser = evaluationWorkbookParser;
    }

    @Transactional(readOnly = true)
    public TypedBenchmarkDataset resolve(UUID evaluationRunId) {
        EvaluationRunEntity run =
                evaluationRunRepository.findByIdFetchDataset(evaluationRunId).orElseThrow(() ->
                        new BenchmarkDatasetResolutionException("evaluation_run not found: " + evaluationRunId));
        if (run.getBenchmarkKind() == null || run.getBenchmarkKind().isBlank()) {
            throw new BenchmarkDatasetResolutionException("evaluation_run missing benchmark_kind");
        }
        BenchmarkKind kind;
        try {
            kind = BenchmarkKind.valueOf(run.getBenchmarkKind().trim());
        } catch (IllegalArgumentException e) {
            throw new BenchmarkDatasetResolutionException("unknown benchmark_kind on run: " + run.getBenchmarkKind());
        }
        EvaluationDatasetEntity ds = run.getDataset();
        ExperimentalDatasetType experimental = BenchmarkDatasetCompatibility.resolveExperimentalType(ds);
        if (!BenchmarkDatasetCompatibility.compatible(experimental, kind)) {
            throw new BenchmarkDatasetResolutionException(
                    "Dataset experimental kind " + experimental + " is not compatible with benchmark " + kind);
        }
        WorkbookParseResult parsed;
        try {
            parsed = parseWorkbook(ds, experimental);
        } catch (IOException e) {
            throw new BenchmarkDatasetResolutionException("Failed to open evaluation dataset bytes", e);
        }
        if (parsed.validationReport().hasErrors()) {
            throw new BenchmarkDatasetResolutionException(parsed.validationReport());
        }
        var wb = parsed.workbook();

        // Hard gate: typed jobs must never run on demo/too-small/incomplete datasets.
        var gate = new ValidationReport();
        LabDatasetGateValidator.validatePreRun(kind, experimental, wb, gate);
        if (gate.hasErrors()) {
            throw new BenchmarkDatasetResolutionException(gate);
        }

        DatasetQuestionSubsetSupport.ResolvedSubset subset =
                DatasetQuestionSubsetSupport.readFromRun(run)
                        .orElse(DatasetQuestionSubsetSupport.ResolvedSubset.all());

        return switch (kind) {
            case LLM_JUDGE_QA -> {
                if (RoleEvalCaseSubsetSupport.isRoleEvalMode(run)) {
                    RoleEvalCaseSubsetSupport.RoleEvalFilter filter =
                            RoleEvalCaseSubsetSupport.resolveFilterFromRun(run);
                    List<LlmRoleEvalCase> cases =
                            RoleEvalCaseSubsetSupport.filter(wb.llmRoleEvalCases(), filter);
                    if (cases.isEmpty()) {
                        throw new BenchmarkDatasetResolutionException(
                                "Parsed workbook has no llm_role_eval_cases rows for role-eval run"
                                        + (filter.unrestricted() ? "" : " after role-eval filter"));
                    }
                    yield new TypedBenchmarkDataset.LlmRoleCases(cases);
                }
                List<LlmReaderQuestion> qs =
                        DatasetQuestionSubsetSupport.filterLlmQuestions(wb.llmReaderQuestions(), subset);
                if (qs.isEmpty()) {
                    throw new BenchmarkDatasetResolutionException(
                            "Parsed workbook has no llm_reader_questions rows for LLM_JUDGE_QA"
                                    + (subset.allQuestions() ? "" : " after dataset question subset filter"));
                }
                yield new TypedBenchmarkDataset.LlmQuestions(qs, wb.corpusDocuments());
            }
            case EMBEDDING_RETRIEVAL -> {
                List<EmbeddingRetrievalQuery> qs =
                        DatasetQuestionSubsetSupport.filterEmbeddingQueries(
                                wb.embeddingRetrievalQueries(), subset);
                if (qs.isEmpty()) {
                    throw new BenchmarkDatasetResolutionException(
                            "Parsed workbook has no embedding_retrieval_queries rows for EMBEDDING_RETRIEVAL"
                                    + (subset.allQuestions() ? "" : " after dataset question subset filter"));
                }
                yield new TypedBenchmarkDataset.EmbeddingQuestions(
                        new EmbeddingRetrievalDataset(qs, wb.chunkRegistry(), wb.corpusDocuments()));
            }
            case RAG_PRESET_END_TO_END -> {
                List<RagPresetQuestion> qs =
                        DatasetQuestionSubsetSupport.filterQuestions(wb.ragPresetQuestionsEnriched(), subset);
                if (qs.isEmpty()) {
                    throw new BenchmarkDatasetResolutionException(
                            "Parsed workbook has no rag_preset_questions_enriched rows for RAG_PRESET_END_TO_END"
                                    + (subset.allQuestions() ? "" : " after dataset question subset filter"));
                }
                yield new TypedBenchmarkDataset.RagPresetQuestions(qs, wb.ragPresetCatalog());
            }
            case CLASSIFIER_METRICS ->
                    throw new BenchmarkDatasetResolutionException(
                            "CLASSIFIER_METRICS uses multipart handler, not typed workbook resolver");
        };
    }

    private WorkbookParseResult parseWorkbook(EvaluationDatasetEntity ds, ExperimentalDatasetType experimental)
            throws IOException {
        try (InputStream in = openDatasetStream(ds)) {
            return evaluationWorkbookParser.parse(in, experimental);
        }
    }

    private InputStream openDatasetStream(EvaluationDatasetEntity ds) throws IOException {
        String uri = ds.getStorageUri();
        if (uri == null || uri.isBlank()) {
            throw new BenchmarkDatasetResolutionException("evaluation_dataset.storage_uri is missing");
        }
        String trimmed = uri.trim();
        if (trimmed.startsWith(CLASSPATH_STORAGE_PREFIX)) {
            String path = trimmed.substring(CLASSPATH_STORAGE_PREFIX.length());
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new BenchmarkDatasetResolutionException("Classpath dataset missing: " + path);
            }
            return resource.getInputStream();
        }
        return evaluationDatasetStorePort.openStream(trimmed);
    }
}
