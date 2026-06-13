package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.evaluation.BenchmarkJobAccepted;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.application.service.evaluation.LabEvaluationRunService;
import com.uniovi.rag.application.service.evaluation.LabDatasetGateException;
import com.uniovi.rag.application.service.evaluation.LabMetricsComparisonService;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.interfaces.rest.dto.CampaignChildRunSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationResultItemDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.LatestLabRunRecoveryDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LabBenchmarkController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabBenchmarkController.class, ApiGlobalExceptionHandler.class})
class LabBenchmarkControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BenchmarkRunOrchestrator benchmarkRunOrchestrator;

    @MockitoBean
    private LabEvaluationRunService labEvaluationRunService;

    @MockitoBean
    private LabMetricsComparisonService labMetricsComparisonService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(apiPathProperties.getProductBasePath()).thenReturn(path(""));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postBenchmark_returns202WithBothIds() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ds = UUID.randomUUID();
        when(benchmarkRunOrchestrator.startJsonBenchmark(
                        eq(userId), eq("USER"), eq(BenchmarkKind.LLM_JUDGE_QA), any(StartBenchmarkRunRequest.class)))
                .thenReturn(BenchmarkJobAccepted.of(runId, taskId));

        String body = String.format(
                "{\"datasetId\":\"%s\",\"runKind\":\"PRODUCT_EXPLORATION\"}", ds);

        mockMvc.perform(post(path("/lab/benchmarks/LLM_JUDGE_QA/runs")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.evaluationRunId").value(runId.toString()))
                .andExpect(jsonPath("$.asyncTaskId").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void postRagBenchmark_withCorpusIdAndNoProjectId_returns202() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID ds = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        when(benchmarkRunOrchestrator.startJsonBenchmark(
                        eq(userId), eq("USER"), eq(BenchmarkKind.RAG_PRESET_END_TO_END), any(StartBenchmarkRunRequest.class)))
                .thenReturn(BenchmarkJobAccepted.of(runId, taskId));

        String body = String.format(
                "{\"datasetId\":\"%s\",\"corpusId\":\"%s\",\"runKind\":\"PRODUCT_EXPLORATION\"}", ds, corpusId);

        mockMvc.perform(
                        post(path("/lab/benchmarks/RAG_PRESET_END_TO_END/runs"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.evaluationRunId").value(runId.toString()));
    }

    @Test
    void postBenchmark_rejectedByDatasetGate_returns422WithStructuredError() throws Exception {
        UUID ds = UUID.randomUUID();
        when(benchmarkRunOrchestrator.startJsonBenchmark(
                        eq(userId), eq("USER"), eq(BenchmarkKind.RAG_PRESET_END_TO_END), any(StartBenchmarkRunRequest.class)))
                .thenThrow(new LabDatasetGateException(
                        "DATASET_TOO_SMALL",
                        "Dataset is not eligible for RAG_PRESET_END_TO_END (see validationIssues).",
                        new ValidationReport()));

        String body = String.format(
                "{\"datasetId\":\"%s\",\"runKind\":\"PRODUCT_EXPLORATION\"}", ds);

        mockMvc.perform(post(path("/lab/benchmarks/RAG_PRESET_END_TO_END/runs")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DATASET_TOO_SMALL"))
                .andExpect(jsonPath("$.error.code").value("DATASET_TOO_SMALL"));
    }

    @Test
    void getRun_returnsDetail() throws Exception {
        UUID runId = UUID.randomUUID();
        EvaluationRunDetailDto dto =
                new EvaluationRunDetailDto(
                        runId,
                        "n",
                        "DONE",
                        "LLM_JUDGE_QA",
                        "SCIENCE",
                        "1.0.0",
                        "abc",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        null,
                        false,
                        null,
                        null,
                        0,
                        0,
                        List.of());
        when(labEvaluationRunService.getRun(userId, runId)).thenReturn(dto);

        mockMvc.perform(get(path("/lab/runs/") + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.benchmarkKind").value("LLM_JUDGE_QA"));
    }

    @Test
    void compare_returnsComparable() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(labEvaluationRunService.compare(userId, a, b))
                .thenReturn(new CompareRunsResponseDto(true, List.of(), a, b));

        mockMvc.perform(get(path("/lab/runs/compare")).param("runA", a.toString()).param("runB", b.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparable").value(true));
    }

    @Test
    void getLatestRun_returnsRecoveryDto() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(labEvaluationRunService.findLatestRunForRecovery(
                        eq(userId), eq(BenchmarkKind.LLM_JUDGE_QA), eq(null)))
                .thenReturn(
                        new LatestLabRunRecoveryDto(
                                runId,
                                taskId,
                                "LLM_JUDGE_QA",
                                null,
                                "SUCCEEDED",
                                true,
                                "/api/v5/lab/jobs/" + taskId,
                                "/api/v5/lab/jobs/" + taskId + "/events",
                                Map.of("ok", true),
                                Instant.parse("2026-01-01T00:00:00Z"),
                                Instant.parse("2026-01-01T00:05:00Z"),
                                true,
                                null,
                                1,
                                List.of()));

        mockMvc.perform(get(path("/lab/benchmarks/LLM_JUDGE_QA/runs/latest")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationRunId").value(runId.toString()))
                .andExpect(jsonPath("$.jobId").value(taskId.toString()))
                .andExpect(jsonPath("$.terminal").value(true))
                .andExpect(jsonPath("$.result.ok").value(true));
    }

    @Test
    void getLatestRun_notFound_returns404() throws Exception {
        when(labEvaluationRunService.findLatestRunForRecovery(
                        eq(userId), eq(BenchmarkKind.LLM_JUDGE_QA), eq(null)))
                .thenReturn(null);

        mockMvc.perform(get(path("/lab/benchmarks/LLM_JUDGE_QA/runs/latest")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRun_campaignCoordinator_returnsCampaignRecoveryFields() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        EvaluationRunDetailDto dto =
                new EvaluationRunDetailDto(
                        runId,
                        "campaign P0",
                        "DONE",
                        "RAG_PRESET_END_TO_END",
                        "PRODUCT_EXPLORATION",
                        "1.0.0",
                        "abc",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        null,
                        null,
                        "gemma3:4b",
                        null,
                        null,
                        Map.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:10:00Z"),
                        campaignId,
                        true,
                        "P0",
                        "PRESET_CODE",
                        60,
                        240,
                        List.of(
                                new CampaignChildRunSummaryDto(
                                        runId, "P0", "Direct LLM", "P0 — Direct LLM", "gemma3:4b", "DONE", 60),
                                new CampaignChildRunSummaryDto(
                                        UUID.randomUUID(), "P1", "Full corpus", "P1 — Full corpus", "gemma3:4b", "DONE", 60)));
        when(labEvaluationRunService.getRun(userId, runId)).thenReturn(dto);

        mockMvc.perform(get(path("/lab/runs/") + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.campaignMode").value(true))
                .andExpect(jsonPath("$.campaignPersistedItemCount").value(240))
                .andExpect(jsonPath("$.campaignChildRuns.length()").value(2));
    }

    @Test
    void listItems_campaignCoordinator_returnsCampaignWideItems() throws Exception {
        UUID runId = UUID.randomUUID();
        List<EvaluationResultItemDto> items =
                java.util.stream.IntStream.range(0, 240)
                        .mapToObj(
                                i ->
                                        new EvaluationResultItemDto(
                                                UUID.randomUUID(),
                                                "q" + i,
                                                "expected",
                                                "actual",
                                                1,
                                                null,
                                                10L,
                                                "RAG_PRESET_END_TO_END",
                                                Map.of("preset_code", i < 60 ? "P0" : "P1"),
                                                Instant.parse("2026-01-01T00:00:00Z")))
                        .toList();
        when(labEvaluationRunService.listItems(userId, runId)).thenReturn(items);

        mockMvc.perform(get(path("/lab/runs/") + runId + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(240));
    }

    @Test
    void getLatestRun_campaign_returnsCampaignRecoveryMetadata() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(labEvaluationRunService.findLatestRunForRecovery(
                        eq(userId), eq(BenchmarkKind.RAG_PRESET_END_TO_END), eq(null)))
                .thenReturn(
                        new LatestLabRunRecoveryDto(
                                runId,
                                taskId,
                                "RAG_PRESET_END_TO_END",
                                null,
                                "SUCCEEDED",
                                true,
                                "/api/v5/lab/jobs/" + taskId,
                                "/api/v5/lab/jobs/" + taskId + "/events",
                                Map.of("resultsSource", "DATABASE", "persistedItemCount", 240),
                                Instant.parse("2026-01-01T00:00:00Z"),
                                Instant.parse("2026-01-01T00:10:00Z"),
                                true,
                                campaignId,
                                240,
                                List.of(runId, UUID.randomUUID())));

        mockMvc.perform(get(path("/lab/benchmarks/RAG_PRESET_END_TO_END/runs/latest")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.persistedItemCount").value(240))
                .andExpect(jsonPath("$.campaignChildRunIds.length()").value(2));
    }

    @Test
    void exportMvpItemsJson_returnsBundle() throws Exception {
        UUID runId = UUID.randomUUID();
        when(labEvaluationRunService.exportMvpItemsJsonBundle(userId, runId))
                .thenReturn(Map.of("mvpSchemaVersion", "1", "items", List.of()));

        mockMvc.perform(get(path("/lab/runs/") + runId + "/export/mvp/items.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mvpSchemaVersion").value("1"));
    }
}
