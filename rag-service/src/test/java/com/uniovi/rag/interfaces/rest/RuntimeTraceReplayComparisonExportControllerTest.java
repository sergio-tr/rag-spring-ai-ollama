package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportArtifact;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonexport.RuntimeTraceReplayComparisonExportSizeExceededException;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceReplayComparisonExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceReplayComparisonExportController.class)
@ActiveProfiles("test")
class RuntimeTraceReplayComparisonExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayComparisonExportService runtimeTraceReplayComparisonExportService;

    private UUID userId;
    private UUID traceId;
    private UUID conversationId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        traceId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();

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
    void query_string_on_trace_export_path_returns_400() throws Exception {
        when(runtimeTraceReplayComparisonExportService.exportByTraceId(any(), any())).thenReturn(sampleZip());
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison/export", traceId).queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_message_export_path_returns_400() throws Exception {
        when(runtimeTraceReplayComparisonExportService.exportByMessageId(any(), any(), any()))
                .thenReturn(sampleZip());
        mockMvc.perform(
                        get(
                                        "/api/v5/conversations/{cid}/messages/{mid}/runtime-trace/replay-comparison/export",
                                        conversationId,
                                        messageId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void not_found_from_service_returns_404() throws Exception {
        when(runtimeTraceReplayComparisonExportService.exportByTraceId(eq(userId), eq(traceId)))
                .thenThrow(new NotFoundException("trace not found"));
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison/export", traceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void size_overflow_returns_413() throws Exception {
        when(runtimeTraceReplayComparisonExportService.exportByTraceId(eq(userId), eq(traceId)))
                .thenThrow(new RuntimeTraceReplayComparisonExportSizeExceededException("too large"));
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison/export", traceId))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void success_returns_zip_with_headers() throws Exception {
        byte[] content = {1, 2, 3};
        String filename = "runtime-trace-replay-comparison_" + traceId + ".zip";
        RuntimeTraceReplayComparisonExportArtifact artifact =
                new RuntimeTraceReplayComparisonExportArtifact(
                        filename,
                        RuntimeTraceReplayComparisonExportArtifact.MEDIA_TYPE_ZIP,
                        content,
                        content.length);
        when(runtimeTraceReplayComparisonExportService.exportByTraceId(eq(userId), eq(traceId)))
                .thenReturn(artifact);
        mockMvc.perform(get("/api/v5/runtime-traces/{traceId}/replay-comparison/export", traceId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + filename + "\""));
    }

    private static RuntimeTraceReplayComparisonExportArtifact sampleZip() {
        byte[] content = {1, 2, 3};
        return new RuntimeTraceReplayComparisonExportArtifact(
                "x.zip", RuntimeTraceReplayComparisonExportArtifact.MEDIA_TYPE_ZIP, content, content.length);
    }
}
