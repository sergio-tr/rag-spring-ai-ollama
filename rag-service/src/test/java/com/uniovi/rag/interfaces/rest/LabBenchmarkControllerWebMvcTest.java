package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.BenchmarkJobAccepted;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.application.service.evaluation.LabEvaluationRunService;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
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
@Import(LabBenchmarkController.class)
class LabBenchmarkControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BenchmarkRunOrchestrator benchmarkRunOrchestrator;

    @MockitoBean
    private LabEvaluationRunService labEvaluationRunService;

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
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
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

        mockMvc.perform(post("/api/v5/lab/benchmarks/LLM_JUDGE_QA/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.evaluationRunId").value(runId.toString()))
                .andExpect(jsonPath("$.asyncTaskId").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
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
                        null);
        when(labEvaluationRunService.getRun(userId, runId)).thenReturn(dto);

        mockMvc.perform(get("/api/v5/lab/runs/" + runId))
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

        mockMvc.perform(get("/api/v5/lab/runs/compare").param("runA", a.toString()).param("runB", b.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparable").value(true));
    }
}
