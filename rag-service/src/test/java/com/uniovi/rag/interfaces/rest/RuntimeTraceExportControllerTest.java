package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportArtifact;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportService;
import com.uniovi.rag.application.service.runtime.traceexport.RuntimeTraceExportSizeLimitExceededException;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceExportController.class)
@ActiveProfiles("test")
class RuntimeTraceExportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RuntimeTraceExportService runtimeTraceExportService;

    private UUID userId;
    private UUID conversationId;
    private UUID messageId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        traceId = UUID.randomUUID();

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
    void exportSingleByTraceId_returnsZip200() throws Exception {
        when(runtimeTraceExportService.exportSingleTraceById(eq(userId), eq(traceId)))
                .thenReturn(new RuntimeTraceExportArtifact("x.zip", "application/zip", new byte[] {1, 2}, 2L, "SINGLE_TRACE"));

        mockMvc.perform(get("/api/v5/runtime-traces/{id}/export", traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"x.zip\""));
    }

    @Test
    void exportSingleByMessageId_returnsZip200() throws Exception {
        when(runtimeTraceExportService.exportSingleTraceByMessageId(eq(userId), eq(conversationId), eq(messageId)))
                .thenReturn(new RuntimeTraceExportArtifact("x.zip", "application/zip", new byte[] {1}, 1L, "SINGLE_TRACE"));

        mockMvc.perform(get("/api/v5/conversations/{cid}/messages/{mid}/runtime-trace/export", conversationId, messageId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void exportConversationBundle_rejectsUnsupportedQueryParam400() throws Exception {
        mockMvc.perform(get("/api/v5/conversations/{cid}/runtime-traces/export?nope=1", conversationId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportSingleByTraceId_rejectsAnyQueryParam400() throws Exception {
        mockMvc.perform(get("/api/v5/runtime-traces/{id}/export?x=1", traceId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportSingleByTraceId_sizeOverflow_returns413() throws Exception {
        when(runtimeTraceExportService.exportSingleTraceById(eq(userId), eq(traceId)))
                .thenThrow(new RuntimeTraceExportSizeLimitExceededException("too big"));

        mockMvc.perform(get("/api/v5/runtime-traces/{id}/export", traceId))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void exportSingleByTraceId_unauthorizedOrNotFound_returns404() throws Exception {
        when(runtimeTraceExportService.exportSingleTraceById(eq(userId), eq(traceId)))
                .thenThrow(new NotFoundException("trace not found"));

        mockMvc.perform(get("/api/v5/runtime-traces/{id}/export", traceId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

