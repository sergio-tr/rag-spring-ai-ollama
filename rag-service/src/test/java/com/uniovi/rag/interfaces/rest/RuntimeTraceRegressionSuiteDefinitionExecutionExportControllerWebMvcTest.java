package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinitionexecutionexport.RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceRegressionSuiteDefinitionExecutionExportController.class)
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionExecutionExportControllerWebMvcTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    private String productBase() {
        return RagApiTestPaths.productBasePath(environment);
    }

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionExecutionExportService exportService;

    private UUID userId;
    private UUID definitionId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        definitionId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
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

    private static RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact sampleZip(String filename) {
        byte[] content = {1, 2, 3};
        return new RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact(
                filename,
                RuntimeTraceRegressionSuiteDefinitionExecutionExportArtifact.MEDIA_TYPE_ZIP,
                content,
                content.length);
    }

    @Test
    void t1_x1_completedAllBatchReturns_zipHeaders() throws Exception {
        String fn = "runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip";
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId))).thenReturn(sampleZip(fn));
        String expectedDisposition =
                "attachment; filename=\"runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip\"";
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, Matchers.containsString("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedDisposition))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsByteArray().length)
                                        .isGreaterThan(0));
    }

    @Test
    void t2_x1_notFound() throws Exception {
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                .thenThrow(new NotFoundException("missing"));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(noZipDownloadResponse());
    }

    @Test
    void t3_x2_notFound() throws Exception {
        when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                .thenThrow(new NotFoundException("missing"));
        mockMvc.perform(
                        post(
                                        productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(noZipDownloadResponse());
    }

    @Test
    void t4_x1_malformedDefinitionId() throws Exception {
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", "not-uuid")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
    }

    @Test
    void t5_x1_queryString_neverCallsExport() throws Exception {
        when(exportService.exportByDefinitionId(any(), any())).thenReturn(sampleZip("x.zip"));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(exportService, never()).exportByDefinitionId(any(), any());
    }

    @Test
    void t6_x1_nonEmptyBody_neverCallsExport() throws Exception {
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"a\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(exportService, never()).exportByDefinitionId(any(), any());
    }

    @Test
    void t7_x2_nonEmptyBody_neverCallsExport() throws Exception {
        mockMvc.perform(
                        post(
                                        productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"a\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
        verify(exportService, never()).exportByDefinitionIdAndConversation(any(), any(), any());
    }

    @Test
    void t8_notAttemptedException_badRequest() throws Exception {
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportNotAttemptedException("x"));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(noZipDownloadResponse());
    }

    @Test
    void t9_emptySuite_zip() throws Exception {
        String fn = "runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip";
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId))).thenReturn(sampleZip(fn));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, Matchers.containsString("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("attachment")));
    }

    @Test
    void t10_completedWithFailures_zip() throws Exception {
        String fn = "runtime-trace-regression-suite-definition-execution_" + definitionId + ".zip";
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId))).thenReturn(sampleZip(fn));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, Matchers.containsString("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("attachment")));
    }

    @Test
    void t11a_x1_sizeExceeded() throws Exception {
        when(exportService.exportByDefinitionId(eq(definitionId), eq(userId)))
                .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException("big"));
        mockMvc.perform(
                        post(productBase() + "/runtime-trace-regression-suite-definitions/{id}/execute/export", definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(noZipDownloadResponse());
        verify(exportService, times(1)).exportByDefinitionId(eq(definitionId), eq(userId));
    }

    @Test
    void t11b_x2_sizeExceeded() throws Exception {
        when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                .thenThrow(new RuntimeTraceRegressionSuiteDefinitionExecutionExportSizeExceededException("big"));
        mockMvc.perform(
                        post(
                                        productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(noZipDownloadResponse());
        verify(exportService, times(1)).exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId));
    }

    @Test
    void t12_x2_zipDispositionContainsConversation() throws Exception {
        String fn =
                "runtime-trace-regression-suite-definition-execution_"
                        + definitionId
                        + "_conversation_"
                        + conversationId
                        + ".zip";
        when(exportService.exportByDefinitionIdAndConversation(eq(definitionId), eq(conversationId), eq(userId)))
                .thenReturn(sampleZip(fn));
        mockMvc.perform(
                        post(
                                        productBase() + "/conversations/{cid}/runtime-trace-regression-suite-definitions/{id}/execute/export",
                                        conversationId,
                                        definitionId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString("_conversation_")));
    }
}
