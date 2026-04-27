package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RunImportZipTestUtil;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimportpreview.RuntimeTraceRegressionSuiteRunImportPreviewService;
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

import com.uniovi.rag.infrastructure.zip.ZipIoGuards;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteRunImportPreviewController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceRegressionSuiteRunImportPreviewController.class)
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteRunImportPreviewControllerWebMvcTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    private String productBase() {
        return RagApiTestPaths.productBasePath(environment);
    }

    private String previewPath() {
        return RagApiTestPaths.path(environment, "/runtime-trace-regression-suite-runs/import/preview");
    }

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportPreviewService previewService;

    private final RuntimeTraceRegressionSuiteRunImportPreviewService realPreviewService =
            new RuntimeTraceRegressionSuiteRunImportPreviewService();

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
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void delegatePreviewToReal() {
        when(previewService.previewImportZip(any())).thenAnswer(inv -> realPreviewService.previewImportZip(inv.getArgument(0)));
    }

    @Test
    void t1_validZip_200_jsonImportableNoLocation() throws Exception {
        delegatePreviewToReal();
        byte[] zip = RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings.length()").value(0))
                .andExpect(jsonPath("$.run.id").value(runId.toString()))
                .andExpect(
                        result -> {
                            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
                            assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LOCATION)).isNull();
                            String ct = result.getResponse().getContentType();
                            assertThat(ct).isNotNull();
                            assertThat(ct).contains("application/json");
                        });
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t2_queryString_400_neverPreview() throws Exception {
        mockMvc.perform(
                        post(previewPath())
                                .queryParam("x", "1")
                                .contentType("application/zip")
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t3_contentTypeNull_400() throws Exception {
        mockMvc.perform(post(previewPath()).content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t4_contentTypeBlank_400() throws Exception {
        mockMvc.perform(
                        post(previewPath())
                                .header(HttpHeaders.CONTENT_TYPE, "   ")
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t5_octetStream_400() throws Exception {
        mockMvc.perform(
                        post(previewPath())
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t6_contentTypeZipWithCharset_400() throws Exception {
        mockMvc.perform(
                        post(previewPath())
                                .contentType("application/zip; charset=UTF-8")
                                .content(RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId)))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t7_emptyBody_400() throws Exception {
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(new byte[0]))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t8_invalidZipStructures_400() throws Exception {
        delegatePreviewToReal();
        byte[] good = RunImportZipTestUtil.buildAdHocEmptyRunZip(runId, userId);
        byte[] man;
        byte[] run;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(good))) {
            var e1 = zin.getNextEntry();
            man = ZipIoGuards.readStoredEntryBytes(zin, e1, RuntimeTraceRegressionSuiteRunImportPreviewService.MAX_PREVIEW_ZIP_BYTES);
            zin.closeEntry();
            var e2 = zin.getNextEntry();
            run = ZipIoGuards.readStoredEntryBytes(zin, e2, RuntimeTraceRegressionSuiteRunImportPreviewService.MAX_PREVIEW_ZIP_BYTES);
        }
        mockMvc.perform(
                        post(previewPath())
                                .contentType("application/zip")
                                .content(RunImportZipTestUtil.buildZipWithDeflatedFirstEntry(man, run)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post(previewPath())
                                .contentType("application/zip")
                                .content(RunImportZipTestUtil.buildZipWithThreeEntries(man, run)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post(previewPath())
                                .contentType("application/zip")
                                .content(RunImportZipTestUtil.buildZipWrongOrder(man, run)))
                .andExpect(status().isBadRequest());
        verify(previewService, times(3)).previewImportZip(any());
    }

    @Test
    void t9_invalidManifestJson_400() throws Exception {
        delegatePreviewToReal();
        byte[] zip = RunImportZipTestUtil.buildZipWithInvalidManifestJson(runId, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t10_manifestFailsChecks_400() throws Exception {
        delegatePreviewToReal();
        byte[] zip = RunImportZipTestUtil.buildZipWithWrongExportKind(runId, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t11_invalidRunJson_400() throws Exception {
        delegatePreviewToReal();
        byte[] zip = RunImportZipTestUtil.buildZipWithInvalidRunJson(runId, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t12_scopeRunIdMismatchTopLevel_400() throws Exception {
        delegatePreviewToReal();
        UUID other = UUID.randomUUID();
        byte[] zip = RunImportZipTestUtil.buildZipWithScopeRunIdMismatch(runId, other, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t13_coherenceMismatch_400() throws Exception {
        delegatePreviewToReal();
        byte[] zip = RunImportZipTestUtil.buildZipWithCoherenceMismatch(runId, userId);
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(zip))
                .andExpect(status().isBadRequest());
        verify(previewService, times(1)).previewImportZip(any());
    }

    @Test
    void t16_bodyTooLarge_400_neverPreview() throws Exception {
        byte[] body = new byte[2097153];
        mockMvc.perform(post(previewPath()).contentType("application/zip").content(body))
                .andExpect(status().isBadRequest());
        verify(previewService, never()).previewImportZip(any());
    }

    @Test
    void t15_meta_webMvcSourceDoesNotRegisterForbiddenMockBeans() throws Exception {
        Path source =
                Path.of(
                        "src/test/java/com/uniovi/rag/interfaces/rest/RuntimeTraceRegressionSuiteRunImportPreviewControllerWebMvcTest.java");
        String text = Files.readString(source).replace("\r", "");
        long mockitoBeanAnnotations =
                text.lines().filter(line -> line.stripLeading().startsWith("@MockitoBean")).count();
        assertThat(mockitoBeanAnnotations).isEqualTo(1);
        assertThat(text)
                .contains(
                        "@MockitoBean\n    private RuntimeTraceRegressionSuiteRunImportPreviewService previewService");
    }
}
