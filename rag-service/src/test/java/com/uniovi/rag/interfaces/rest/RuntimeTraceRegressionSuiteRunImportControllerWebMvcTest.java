package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RunImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunImportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteRunImportController.class, RuntimeTraceRegressionSuiteRunImportControllerWebMvcTest.ImportTestConfig.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteRunImportControllerWebMvcTest {

    @TestConfiguration
    static class ImportTestConfig {

        @Bean
        RuntimeTraceRegressionSuiteRunImportService runtimeTraceRegressionSuiteRunImportService(
                RuntimeTraceRegressionSuiteRunPersistenceService persistenceService) {
            return new RuntimeTraceRegressionSuiteRunImportService(persistenceService);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService persistence;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    private UUID userId;
    private UUID runIdInFixture;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        runIdInFixture = UUID.randomUUID();
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

    @Test
    void t1_validZip_201_location_emptyBody() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(persistence.createRun(eq(userId), any(), any(), any())).thenReturn(createdId);
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId);
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-runs/import")
                                .contentType("application/zip")
                                .content(zip))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/test/runtime-trace-regression-suite-runs/" + createdId))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray().length).isZero());
        verify(persistence, times(1)).createRun(eq(userId), any(), any(), any());
        verify(persistence, never()).loadByIdForUser(any(), any());
        verify(persistence, never()).listSummariesForUser(any());
        verifyNoMoreInteractions(persistence);
        verify(suiteService, never()).execute(any());
        verify(definitionService, never()).create(any(), any());
    }

    @Test
    void t2_queryString_400_neverCreateRun() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-runs/import")
                                .queryParam("x", "1")
                                .contentType("application/zip")
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId)))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t3_contentTypeWithCharset_400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-runs/import")
                                .contentType("application/zip; charset=UTF-8")
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId)))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t4_octetStream_400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-runs/import")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId)))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t5_emptyBody_400() throws Exception {
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(new byte[0]))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t6_invalidZipStructures_400() throws Exception {
        byte[] good = RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId);
        byte[] man;
        byte[] run;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(good))) {
            var e1 = zin.getNextEntry();
            man = zin.readNBytes((int) e1.getSize());
            zin.closeEntry();
            var e2 = zin.getNextEntry();
            run = zin.readNBytes((int) e2.getSize());
        }
        byte[] deflated = RunImportZipTestUtil.buildZipWithDeflatedFirstEntry(man, run);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(deflated))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());

        byte[] three = RunImportZipTestUtil.buildZipWithThreeEntries(man, run);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(three))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());

        byte[] wrongOrder = RunImportZipTestUtil.buildZipWrongOrder(man, run);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(wrongOrder))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t7_invalidRunJson_400() throws Exception {
        byte[] zip = RunImportZipTestUtil.buildZipWithInvalidRunJson(runIdInFixture, userId);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t8_coherenceMismatch_400() throws Exception {
        byte[] zip = RunImportZipTestUtil.buildZipWithCoherenceMismatch(runIdInFixture, userId);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }

    @Test
    void t9_locationUuidDiffersFromRunJsonId() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(persistence.createRun(eq(userId), any(), any(), any())).thenReturn(createdId);
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runIdInFixture, userId);
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(zip))
                .andExpect(status().isCreated())
                .andExpect(
                        result -> {
                            String loc = result.getResponse().getHeader(HttpHeaders.LOCATION);
                            assertThat(loc).endsWith("/" + createdId);
                            assertThat(createdId).isNotEqualTo(runIdInFixture);
                        });
    }

    @Test
    void t11_bodyTooLarge_400() throws Exception {
        byte[] body = new byte[2097153];
        mockMvc.perform(post("/api/test/runtime-trace-regression-suite-runs/import").contentType("application/zip").content(body))
                .andExpect(status().isBadRequest());
        verify(persistence, never()).createRun(any(), any(), any(), any());
    }
}
