package com.uniovi.rag.interfaces.rest;


import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportArtifact;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiteexport.RuntimeTraceRegressionSuiteExportSizeExceededException;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteExportController.class, RegressionSuiteRestJacksonConfiguration.class})
@ActiveProfiles("test")
class RuntimeTraceRegressionSuiteExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteExportService exportService;

    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
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

    @Test
    void controller_exposes_exactly_two_post_mappings() {
        long n =
                Arrays.stream(RuntimeTraceRegressionSuiteExportController.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(PostMapping.class))
                        .count();
        assertThat(n).isEqualTo(2);
    }

    @Test
    void explicit_route_unknown_field_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/regression-suite/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[],\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_returns_400() throws Exception {
        when(exportService.exportExplicit(eq(userId), any(), any())).thenReturn(sampleZip("runtime-trace-regression-suite.zip"));
        mockMvc.perform(
                        post(path("/runtime-traces/regression-suite/export"))
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void empty_entries_returns_200_zip() throws Exception {
        when(exportService.exportExplicit(eq(userId), any(), any()))
                .thenReturn(sampleZip("runtime-trace-regression-suite.zip"));
        mockMvc.perform(
                        post(path("/runtime-traces/regression-suite/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"runtime-trace-regression-suite.zip\""))
                .andExpect(header().string("Content-Length", "3"));
    }

    @Test
    void not_attempted_returns_400() throws Exception {
        when(exportService.exportExplicit(eq(userId), any(), any()))
                .thenThrow(new RuntimeTraceRegressionSuiteExportNotAttemptedException("x"));
        mockMvc.perform(
                        post(path("/runtime-traces/regression-suite/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size_overflow_returns_413() throws Exception {
        when(exportService.exportExplicit(eq(userId), any(), any()))
                .thenThrow(new RuntimeTraceRegressionSuiteExportSizeExceededException("too large"));
        mockMvc.perform(
                        post(path("/runtime-traces/regression-suite/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void conversation_route_invalid_uuid_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/conversations/not-a-uuid/runtime-traces/regression-suite/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conversation_export_200_never_404() throws Exception {
        String fn = "runtime-trace-regression-suite_conversation_" + conversationId + ".zip";
        when(exportService.exportConversationScoped(eq(userId), any(), eq(conversationId), any()))
                .thenReturn(sampleZip(fn));
        mockMvc.perform(
                        post(path("/conversations/{cid}/runtime-traces/regression-suite/export"), conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"entries\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fn + "\""));
    }

    private static RuntimeTraceRegressionSuiteExportArtifact sampleZip(String filename) {
        byte[] content = {1, 2, 3};
        return new RuntimeTraceRegressionSuiteExportArtifact(
                filename, RuntimeTraceRegressionSuiteExportArtifact.MEDIA_TYPE_ZIP, content, content.length);
    }
}
