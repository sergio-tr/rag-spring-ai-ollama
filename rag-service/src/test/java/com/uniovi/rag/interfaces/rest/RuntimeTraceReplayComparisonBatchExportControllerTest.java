package com.uniovi.rag.interfaces.rest;


import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportArtifact;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportSizeExceededException;
import com.uniovi.rag.configuration.TraceComparisonBatchRestJacksonConfiguration;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceReplayComparisonBatchExportController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceReplayComparisonBatchExportController.class, TraceComparisonBatchRestJacksonConfiguration.class})
@ActiveProfiles("test")
class RuntimeTraceReplayComparisonBatchExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceReplayComparisonBatchExportService batchExportService;

    private UUID userId;
    private UUID conversationId;
    private UUID traceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
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
    void controller_exposes_exactly_two_post_mappings() {
        long n =
                Arrays.stream(RuntimeTraceReplayComparisonBatchExportController.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(PostMapping.class))
                        .count();
        Assertions.assertThat(n).isEqualTo(2);
    }

    @Test
    void query_string_on_trace_export_returns_400() throws Exception {
        when(batchExportService.exportByTraceIds(any(), any())).thenReturn(sampleZip("runtime-trace-replay-comparisons-batch.zip"));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .queryParam("x", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_string_on_conversation_export_returns_400() throws Exception {
        when(batchExportService.exportByConversation(any(), any(), any(), any(), any()))
                .thenReturn(sampleZip("z.zip"));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch/export"),
                                        conversationId)
                                .queryParam("y", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknown_field_on_trace_route_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[],\"extra\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trace_ids_size_over_50_returns_400() throws Exception {
        StringBuilder sb = new StringBuilder("{\"traceIds\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(UUID.randomUUID()).append('"');
        }
        sb.append("]}");
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void null_trace_id_element_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void not_attempted_returns_400() throws Exception {
        when(batchExportService.exportByTraceIds(eq(userId), any()))
                .thenThrow(new RuntimeTraceReplayComparisonBatchExportNotAttemptedException("x"));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + traceId + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void empty_trace_ids_returns_200_zip() throws Exception {
        when(batchExportService.exportByTraceIds(eq(userId), eq(List.of())))
                .thenReturn(sampleZip("runtime-trace-replay-comparisons-batch.zip"));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"runtime-trace-replay-comparisons-batch.zip\""));
    }

    @Test
    void conversation_not_found_returns_404() throws Exception {
        when(batchExportService.exportByConversation(eq(userId), eq(conversationId), any(), any(), any()))
                .thenThrow(new NotFoundException("conversation not found"));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch/export"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversation_empty_selection_returns_200() throws Exception {
        String fn = "runtime-trace-replay-comparisons-batch_conversation_" + conversationId + ".zip";
        when(batchExportService.exportByConversation(eq(userId), eq(conversationId), any(), any(), any()))
                .thenReturn(sampleZip(fn));
        mockMvc.perform(
                        post(
                                        path("/conversations/{cid}/runtime-traces/replay-comparisons/batch/export"),
                                        conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + fn + "\""));
    }

    @Test
    void trace_export_never_404_after_valid_body() throws Exception {
        when(batchExportService.exportByTraceIds(eq(userId), any())).thenReturn(sampleZip("runtime-trace-replay-comparisons-batch.zip"));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void size_overflow_returns_413() throws Exception {
        when(batchExportService.exportByTraceIds(eq(userId), any()))
                .thenThrow(new RuntimeTraceReplayComparisonBatchExportSizeExceededException("too large"));
        mockMvc.perform(
                        post(path("/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"traceIds\":[]}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void malformed_conversation_uuid_returns_400() throws Exception {
        mockMvc.perform(
                        post(path("/conversations/not-a-uuid/runtime-traces/replay-comparisons/batch/export"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static RuntimeTraceReplayComparisonBatchExportArtifact sampleZip(String filename) {
        byte[] content = {1, 2, 3};
        return new RuntimeTraceReplayComparisonBatchExportArtifact(
                filename, RuntimeTraceReplayComparisonBatchExportArtifact.MEDIA_TYPE_ZIP, content, content.length);
    }
}
