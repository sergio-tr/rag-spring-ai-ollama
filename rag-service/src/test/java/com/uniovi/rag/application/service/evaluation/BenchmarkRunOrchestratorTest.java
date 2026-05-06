package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class BenchmarkRunOrchestratorTest {

    @Mock private UserRepository userRepository;
    @Mock private EvaluationDatasetRepository evaluationDatasetRepository;
    @Mock private EvaluationCampaignRepository evaluationCampaignRepository;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private RagPresetRepository ragPresetRepository;
    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private AsyncTaskService asyncTaskService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private RagRuntimeProperties ragRuntimeProperties;
    @Mock private EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser = new EvaluationWorkbookParser();

    @Test
    void startJsonBenchmark_forbidsAdminBaselineForNonAdmin() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        EvaluationRunKind.ADMIN_BASELINE,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void startJsonBenchmark_returnsNotFoundWhenDatasetMissing() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        UUID dsId = UUID.randomUUID();
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.empty());
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "ADMIN", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void startJsonBenchmark_rejectsIncompatibleExperimentalKind() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getExperimentalKind()).thenReturn("LLM_MODEL_BASELINE");
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJsonBenchmark_rejectsDemoOrTooSmallReferenceBundle_beforeCreatingRun() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
                        userRepository,
                        evaluationDatasetRepository,
                        evaluationCampaignRepository,
                        evaluationRunRepository,
                        resolvedConfigSnapshotRepository,
                        knowledgeIndexSnapshotRepository,
                        ragPresetRepository,
                        asyncTaskRepository,
                        asyncTaskService,
                        projectAccessService,
                        ragRuntimeProperties,
                        evaluationDatasetStorePort,
                        evaluationWorkbookParser);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Create a tiny workbook with explicit demo strings.
        byte[] bytes = demoReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/demo.xlsx");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/demo.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null);

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(LabDatasetGateException.class)
                .satisfies(ex -> assertThat(((LabDatasetGateException) ex).code()).isIn(
                        "DATASET_TOO_SMALL",
                        "DATASET_DEMO_CONTENT_DETECTED",
                        "EXPERIMENTAL_DATASET_INVALID"));
    }

    private static byte[] demoReferenceBundleBytes() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Minimal required reference sheets (headers + single row), with demo content.
            Sheet readme = wb.createSheet("README");
            readme.createRow(0).createCell(0).setCellValue("Item");
            readme.getRow(0).createCell(1).setCellValue("Decision");
            readme.createRow(1).createCell(0).setCellValue("Protocol version");
            readme.getRow(1).createCell(1).setCellValue("demo");

            Sheet corpus = wb.createSheet("corpus_documents");
            corpus.createRow(0).createCell(0).setCellValue("document_id");
            corpus.createRow(1).createCell(0).setCellValue("DOC_1");

            Sheet chunks = wb.createSheet("chunk_registry");
            chunks.createRow(0).createCell(0).setCellValue("chunk_id");
            chunks.getRow(0).createCell(1).setCellValue("document_id");
            chunks.createRow(1).createCell(0).setCellValue("CHUNK_1");
            chunks.getRow(1).createCell(1).setCellValue("DOC_1");

            Sheet llm = wb.createSheet("llm_reader_questions");
            llm.createRow(0).createCell(0).setCellValue("id");
            llm.getRow(0).createCell(1).setCellValue("question");
            llm.createRow(1).createCell(0).setCellValue("LLM_Q1");
            llm.getRow(1).createCell(1).setCellValue("Provide a grounded summary of the sample acta.");

            Sheet emb = wb.createSheet("embedding_retrieval_queries");
            emb.createRow(0).createCell(0).setCellValue("id");
            emb.getRow(0).createCell(1).setCellValue("query");
            emb.createRow(1).createCell(0).setCellValue("EMB_Q1");
            emb.getRow(1).createCell(1).setCellValue("sample acta evidence");

            Sheet rag = wb.createSheet("rag_preset_questions_enriched");
            rag.createRow(0).createCell(0).setCellValue("id");
            rag.getRow(0).createCell(1).setCellValue("question");
            rag.getRow(0).createCell(2).setCellValue("expected_answer");
            rag.createRow(1).createCell(0).setCellValue("RAG_Q1");
            rag.getRow(1).createCell(1).setCellValue("What does the sample acta contain?");
            rag.getRow(1).createCell(2).setCellValue("Evidence: Acta sample 1");

            Sheet llmCand = wb.createSheet("llm_candidates");
            llmCand.createRow(0).createCell(0).setCellValue("candidate_id");
            llmCand.createRow(1).createCell(0).setCellValue("c1");

            Sheet embCand = wb.createSheet("embedding_candidates");
            embCand.createRow(0).createCell(0).setCellValue("candidate_id");
            embCand.createRow(1).createCell(0).setCellValue("c1");

            Sheet catalog = wb.createSheet("rag_preset_catalog_P0_P14");
            catalog.createRow(0).createCell(0).setCellValue("preset_id");
            catalog.createRow(1).createCell(0).setCellValue("P0");

            Sheet metric = wb.createSheet("metric_spec");
            metric.createRow(0).createCell(0).setCellValue("metric_id");
            metric.createRow(1).createCell(0).setCellValue("m1");

            Sheet schema = wb.createSheet("result_schema");
            schema.createRow(0).createCell(0).setCellValue("field");
            schema.createRow(1).createCell(0).setCellValue("outcome");

            Sheet summary = wb.createSheet("summary_counts");
            summary.createRow(0).createCell(0).setCellValue("Dataset");
            summary.createRow(1).createCell(0).setCellValue("REFERENCE_BUNDLE");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}

