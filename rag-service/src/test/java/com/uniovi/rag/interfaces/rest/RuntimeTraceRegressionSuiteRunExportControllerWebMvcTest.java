package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunId;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteRunExportController.class, RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest.ExportServiceTestConfig.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteRunExportControllerWebMvcTest {

    @TestConfiguration
    static class ExportServiceTestConfig {

        @Bean
        RuntimeTraceRegressionSuiteRunExportService runtimeTraceRegressionSuiteRunExportService(
                RuntimeTraceRegressionSuiteRunPersistenceService persistenceService) {
            return new RuntimeTraceRegressionSuiteRunExportService(persistenceService);
        }
    }

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    private String productBase() {
        return RagApiTestPaths.productBasePath(environment);
    }

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    private UUID userId;
    private UUID runId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        runId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static ResultMatcher noZipDownloadResponse() {
        return result -> {
            assertThat(result.getResponse().getContentAsByteArray().length).isZero();
            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
            String ct = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
            assertThat(
                            ct == null
                                    || (!ct.equalsIgnoreCase("application/zip")
                                            && !ct.toLowerCase(Locale.ROOT).startsWith("application/zip;")))
                    .isTrue();
        };
    }

    private static RuntimeTraceRegressionSuiteRunSnapshot minimalSnapshot(UUID id, UUID ownerUserId) {
        return new RuntimeTraceRegressionSuiteRunSnapshot(
                new RuntimeTraceRegressionSuiteRunId(id),
                ownerUserId,
                RuntimeTraceRegressionSuiteRunSourceType.AD_HOC,
                Optional.empty(),
                RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS,
                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                Instant.parse("2024-03-01T12:00:00Z"),
                List.of());
    }

    @Test
    void t1_getExport200_zipHeaders() throws Exception {
        when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId)))
                .thenReturn(Optional.of(minimalSnapshot(runId, userId)));
        String expectedDisposition = "attachment; filename=\"runtime-trace-regression-suite-run_" + runId + ".zip\"";
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
                .andExpect(
                        result -> {
                            byte[] body = result.getResponse().getContentAsByteArray();
                            String cl = result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH);
                            assertThat(cl).isEqualTo(Long.toString(body.length));
                            assertThat(body.length).isGreaterThan(0);
                        });
        verify(runPersistenceService, times(1)).loadByIdForUser(eq(runId), eq(userId));
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verifyNoMoreInteractions(runPersistenceService);
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t2_queryString_returns400_neverCallsLoad() throws Exception {
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId).queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t3_malformedUuid_returns400_neverCallsLoad() throws Exception {
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(runPersistenceService, never()).loadByIdForUser(any(), any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t4_emptyOptional_returns404_loadOnce_verifyNoMoreInteractions() throws Exception {
        when(runPersistenceService.loadByIdForUser(eq(runId), eq(userId))).thenReturn(Optional.empty());
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", runId))
                .andExpect(status().isNotFound())
                .andExpect(noZipDownloadResponse());
        verify(runPersistenceService, times(1)).loadByIdForUser(eq(runId), eq(userId));
        verifyNoMoreInteractions(runPersistenceService);
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t6_secondRunId_sameVerifiesAsT1() throws Exception {
        UUID otherRunId = UUID.randomUUID();
        when(runPersistenceService.loadByIdForUser(eq(otherRunId), eq(userId)))
                .thenReturn(Optional.of(minimalSnapshot(otherRunId, userId)));
        mockMvc.perform(get(productBase() + "/runtime-trace-regression-suite-runs/{id}/export", otherRunId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"runtime-trace-regression-suite-run_" + otherRunId + ".zip\""));
        verify(runPersistenceService, times(1)).loadByIdForUser(eq(otherRunId), eq(userId));
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
        verify(runPersistenceService, never()).listSummariesForUser(any());
        verifyNoMoreInteractions(runPersistenceService);
        verify(suiteService, never()).execute(any());
    }
}
