package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeTraceController.class)
@ActiveProfiles("test")
class RuntimeTraceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RuntimeTraceQueryService runtimeTraceQueryService;

    private UUID userId;
    private UUID conversationId;
    private UUID traceId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        traceId = UUID.randomUUID();
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
    void listConversationTraces_returnsPage() throws Exception {
        RuntimeExecutionTraceSummaryDto s =
                new RuntimeExecutionTraceSummaryDto(
                        traceId,
                        Instant.now(),
                        userId,
                        UUID.randomUUID(),
                        conversationId,
                        messageId,
                        "corr",
                        null,
                        null,
                        "wf",
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        "");
        Page<RuntimeExecutionTraceSummaryDto> page = new PageImpl<>(List.of(s));
        when(runtimeTraceQueryService.listConversationTraceSummaries(
                eq(userId),
                eq(conversationId),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()))
                .thenReturn(page);

        mockMvc.perform(get(path("/conversations/{id}/runtime-traces"), conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(traceId.toString()));
    }

    @Test
    void getTraceById_returnsDetail() throws Exception {
        RuntimeExecutionTraceDetailDto d =
                new RuntimeExecutionTraceDetailDto(
                        traceId,
                        Instant.now(),
                        userId,
                        UUID.randomUUID(),
                        conversationId,
                        messageId,
                        "corr",
                        null,
                        null,
                        "wf",
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        1,
                        Map.of(),
                        List.of());
        when(runtimeTraceQueryService.getTraceDetailById(eq(userId), eq(traceId))).thenReturn(d);

        mockMvc.perform(get(path("/runtime-traces/{id}"), traceId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(traceId.toString()));
    }

    @Test
    void getTraceByMessage_returnsDetail() throws Exception {
        RuntimeExecutionTraceDetailDto d =
                new RuntimeExecutionTraceDetailDto(
                        traceId,
                        Instant.now(),
                        userId,
                        UUID.randomUUID(),
                        conversationId,
                        messageId,
                        "corr",
                        null,
                        null,
                        "wf",
                        false,
                        "",
                        false,
                        "",
                        "",
                        false,
                        false,
                        "",
                        "",
                        "",
                        false,
                        "",
                        "",
                        false,
                        "",
                        1,
                        Map.of(),
                        List.of());
        when(runtimeTraceQueryService.getMostRecentTraceDetailByMessageId(eq(userId), eq(conversationId), eq(messageId)))
                .thenReturn(d);

        mockMvc.perform(get(path("/conversations/{cid}/messages/{mid}/runtime-trace"), conversationId, messageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(messageId.toString()));
    }
}

