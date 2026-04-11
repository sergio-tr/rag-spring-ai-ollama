package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexport.RuntimeTraceRegressionSuiteDefinitionExportService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    RuntimeTraceRegressionSuiteDefinitionExportController.class,
    RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest.ExportServiceTestConfig.class
})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionExportControllerWebMvcTest {

    @TestConfiguration
    static class ExportServiceTestConfig {

        @Bean
        RuntimeTraceRegressionSuiteDefinitionExportService runtimeTraceRegressionSuiteDefinitionExportService(
                RuntimeTraceRegressionSuiteDefinitionService definitionService) {
            return new RuntimeTraceRegressionSuiteDefinitionExportService(definitionService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    private UUID userId;
    private UUID definitionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
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

    private static RuntimeTraceRegressionSuiteDefinitionSnapshot minimalSnapshot(UUID id) {
        return new RuntimeTraceRegressionSuiteDefinitionSnapshot(
                id,
                "n",
                null,
                1,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds(List.of(UUID.randomUUID()))));
    }

    @Test
    void t1_getExport200_zipHeaders() throws Exception {
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId)))
                .thenReturn(Optional.of(minimalSnapshot(definitionId)));
        String expectedDisposition =
                "attachment; filename=\"runtime-trace-regression-suite-definition_" + definitionId + ".zip\"";
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
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
    }

    @Test
    void t2_queryString_returns400_neverCallsLoad() throws Exception {
        mockMvc.perform(
                        get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", definitionId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t3_malformedUuid_returns400_neverCallsLoad() throws Exception {
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t4_emptyOptional_returns404_loadOnce_verifyNoMoreInteractions() throws Exception {
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId))).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
                .andExpect(status().isNotFound())
                .andExpect(noZipDownloadResponse());
        verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
        verifyNoMoreInteractions(definitionService);
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t6_success_neverMaterializes_loadOnce_verifyNoMoreInteractions() throws Exception {
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId)))
                .thenReturn(Optional.of(minimalSnapshot(definitionId)));
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
                .andExpect(status().isOk());
        verify(definitionService, times(1)).loadByIdForUser(eq(definitionId), eq(userId));
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
        verifyNoMoreInteractions(definitionService);
        verify(suiteService, never()).execute(any());
    }

    @Test
    void t7_suiteService_neverExecute() throws Exception {
        when(definitionService.loadByIdForUser(eq(definitionId), eq(userId)))
                .thenReturn(Optional.of(minimalSnapshot(definitionId)));
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions/{id}/export", definitionId))
                .andExpect(status().isOk());
        verify(suiteService, never()).execute(any());
    }
}
