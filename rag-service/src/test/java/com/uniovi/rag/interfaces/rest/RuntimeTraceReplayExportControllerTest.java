package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportArtifact;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportService;
import com.uniovi.rag.application.service.runtime.tracereplayexport.RuntimeTraceReplayExportSizeExceededException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceReplayExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceReplayExportController.class)
@ActiveProfiles("test")
class RuntimeTraceReplayExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayExportService runtimeTraceReplayExportService;

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
    void query_string_on_trace_export_returns_400() throws Exception {
        when(runtimeTraceReplayExportService.exportByTraceId(any(), any())).thenReturn(sampleZip(traceId));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), traceId).queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_message_export_returns_400() throws Exception {
        when(runtimeTraceReplayExportService.exportByMessageId(any(), any(), any()))
                .thenReturn(sampleZipMessage(messageId));
        mockMvc.perform(
                        get(
                                        path("/conversations/{cid}/messages/{mid}/runtime-trace/replay/export"),
                                        conversationId,
                                        messageId)
                                .queryParam("x", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void not_found_returns_404() throws Exception {
        when(runtimeTraceReplayExportService.exportByTraceId(any(), any()))
                .thenThrow(new NotFoundException("trace not found"));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), traceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void success_returns_zip_with_headers() throws Exception {
        RuntimeTraceReplayExportArtifact artifact = sampleZip(traceId);
        when(runtimeTraceReplayExportService.exportByTraceId(any(), any())).thenReturn(artifact);
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Length", String.valueOf(artifact.sizeBytes())))
                .andExpect(
                        header()
                                .string(
                                        "Content-Disposition",
                                        "attachment; filename=\"runtime-trace-replay_" + traceId + ".zip\""));
    }

    @Test
    void unsupported_replay_still_returns_200_zip_when_service_returns() throws Exception {
        when(runtimeTraceReplayExportService.exportByTraceId(any(), any())).thenReturn(sampleZip(traceId));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void oversize_returns_413() throws Exception {
        when(runtimeTraceReplayExportService.exportByTraceId(any(), any()))
                .thenThrow(new RuntimeTraceReplayExportSizeExceededException("too large"));
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), traceId))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void malformed_uuid_returns_400() throws Exception {
        mockMvc.perform(get(path("/runtime-traces/{traceId}/replay/export"), "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private static RuntimeTraceReplayExportArtifact sampleZip(UUID tid) {
        byte[] content = new byte[] {0x50, 0x4b, 0x03, 0x04};
        return new RuntimeTraceReplayExportArtifact(
                "runtime-trace-replay_" + tid + ".zip",
                RuntimeTraceReplayExportArtifact.MEDIA_TYPE_ZIP,
                content,
                content.length);
    }

    private static RuntimeTraceReplayExportArtifact sampleZipMessage(UUID mid) {
        byte[] content = new byte[] {0x50, 0x4b, 0x03, 0x04};
        return new RuntimeTraceReplayExportArtifact(
                "runtime-trace-replay_message_" + mid + ".zip",
                RuntimeTraceReplayExportArtifact.MEDIA_TYPE_ZIP,
                content,
                content.length);
    }
}
