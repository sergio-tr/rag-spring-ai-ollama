package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunexport.RuntimeTraceRegressionSuiteRunExportService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterunimport.RuntimeTraceRegressionSuiteRunImportService;
import com.uniovi.rag.configuration.RegressionSuiteDefinitionMutationJacksonConfiguration;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeTraceRegressionSuiteDefinitionController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RuntimeTraceRegressionSuiteDefinitionController.class, RegressionSuiteDefinitionMutationJacksonConfiguration.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/test")
class RuntimeTraceRegressionSuiteDefinitionControllerMutationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeTraceRegressionSuiteDefinitionService definitionService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteService suiteService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunExportService runExportService;

    @MockitoBean
    private RuntimeTraceRegressionSuiteRunImportService runImportService;

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
        verify(suiteService, never()).execute(any());
        verify(runPersistenceService, never()).createRun(any(), any(), any(), any());
    }

    private static String upsertOneByTraceIds(String name, String traceId) {
        return """
                {"name":"%s","description":null,"entries":[{"entryKind":"BY_TRACE_IDS","traceIds":["%s"]}]}"""
                .formatted(name, traceId);
    }

    @Test
    void t1_create_valid_returns201_location_emptyBody() throws Exception {
        UUID created = UUID.randomUUID();
        UUID tid = UUID.randomUUID();
        when(definitionService.create(eq(userId), any())).thenReturn(created);
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isCreated())
                .andExpect(content().string(""))
                .andExpect(
                        header()
                                .string(
                                        "Location",
                                        "/api/test/runtime-trace-regression-suite-definitions/" + created));
        verify(definitionService).create(eq(userId), any());
    }

    @Test
    void t2_update_valid_returns204_emptyBody() throws Exception {
        UUID tid = UUID.randomUUID();
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(definitionService).update(eq(definitionId), eq(userId), any());
    }

    @Test
    void t3_delete_valid_returns204_emptyBody() throws Exception {
        mockMvc.perform(delete("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(definitionService).delete(eq(definitionId), eq(userId));
    }

    @Test
    void t4_create_throwsIllegalState_returns409_emptyBody() throws Exception {
        UUID tid = UUID.randomUUID();
        when(definitionService.create(eq(userId), any())).thenThrow(new IllegalStateException("dup"));
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    @Test
    void t5_update_throwsIllegalState_returns409_emptyBody() throws Exception {
        UUID tid = UUID.randomUUID();
        doThrow(new IllegalStateException("dup"))
                .when(definitionService)
                .update(eq(definitionId), eq(userId), any());
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isConflict())
                .andExpect(content().string(""));
    }

    @Test
    void t6_update_throwsNotFound_returns404_emptyBody() throws Exception {
        UUID tid = UUID.randomUUID();
        doThrow(new NotFoundException("missing"))
                .when(definitionService)
                .update(eq(definitionId), eq(userId), any());
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void t7_delete_throwsNotFound_returns404_emptyBody() throws Exception {
        doThrow(new NotFoundException("missing"))
                .when(definitionService)
                .delete(eq(definitionId), eq(userId));
        mockMvc.perform(delete("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void t8_m2_nonUuidDefinitionId_returns400_neverCallsUpdate() throws Exception {
        UUID tid = UUID.randomUUID();
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", "not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).update(any(), any(), any());
    }

    @Test
    void t8_m3_nonUuidDefinitionId_returns400_neverCallsDelete() throws Exception {
        mockMvc.perform(delete("/api/test/runtime-trace-regression-suite-definitions/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).delete(any(), any());
    }

    @Test
    void t9_mutations_withQueryString_return400_noServiceCalls() throws Exception {
        UUID tid = UUID.randomUUID();
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .queryParam("a", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).create(any(), any());

        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .queryParam("a", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).update(any(), any(), any());

        mockMvc.perform(
                        delete("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .queryParam("a", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).delete(any(), any());
    }

    @Test
    void t10_create_unknownTopLevelKey_returns400() throws Exception {
        UUID tid = UUID.randomUUID();
        String json =
                """
                {"name":"n","description":null,"entries":[{"entryKind":"BY_TRACE_IDS","traceIds":["%s"]}],"extra":1}"""
                        .formatted(tid);
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t11_create_entriesSizeZero_returns400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"n\",\"description\":null,\"entries\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t11b_create_entriesSize21_returns400() throws Exception {
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            if (i > 0) {
                entries.append(',');
            }
            entries.append("{\"entryKind\":\"BY_TRACE_IDS\",\"traceIds\":[]}");
        }
        String json = "{\"name\":\"n\",\"description\":null,\"entries\":[" + entries + "]}";
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t12_create_entriesContainsNull_returns400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"n\",\"description\":null,\"entries\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t13_create_traceIdsContainsNull_returns400() throws Exception {
        UUID a = UUID.randomUUID();
        String json =
                "{\"name\":\"n\",\"description\":null,\"entries\":[{\"entryKind\":\"BY_TRACE_IDS\",\"traceIds\":[\""
                        + a
                        + "\",null]}]}";
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t14_create_fiftyOneTraceIds_returns400() throws Exception {
        String ids =
                IntStream.range(0, 51)
                        .mapToObj(i -> UUID.randomUUID().toString())
                        .map(u -> "\"" + u + "\"")
                        .collect(Collectors.joining(","));
        String json =
                "{\"name\":\"n\",\"description\":null,\"entries\":[{\"entryKind\":\"BY_TRACE_IDS\",\"traceIds\":["
                        + ids
                        + "]}]}";
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t15_create_byConversationMissingConversationId_returns400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"n\",\"description\":null,\"entries\":[{\"entryKind\":\"BY_CONVERSATION\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t16_post_jsonContentType_noBody_returns400() throws Exception {
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t17_put_truncatedJson_returns400() throws Exception {
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"x\",\"entries\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t18_create_success_neverCallsReadOrMaterialize() throws Exception {
        UUID created = UUID.randomUUID();
        UUID tid = UUID.randomUUID();
        when(definitionService.create(eq(userId), any())).thenReturn(created);
        mockMvc.perform(
                        post("/api/test/runtime-trace-regression-suite-definitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upsertOneByTraceIds("suite", tid.toString())))
                .andExpect(status().isCreated());
        verify(definitionService, never()).listSummariesForUser(any());
        verify(definitionService, never()).loadByIdForUser(any(), any());
        verify(definitionService, never()).materializeToSuiteRequest(any(), any());
    }

    @Test
    void t19_update_unknownTopLevelKey_returns400() throws Exception {
        UUID tid = UUID.randomUUID();
        String json =
                """
                {"name":"n","description":null,"entries":[{"entryKind":"BY_TRACE_IDS","traceIds":["%s"]}],"who":1}"""
                        .formatted(tid);
        mockMvc.perform(
                        put("/api/test/runtime-trace-regression-suite-definitions/{id}", definitionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void t20_getList_withQueryParam_returns400_neverCallsListSummaries() throws Exception {
        mockMvc.perform(get("/api/test/runtime-trace-regression-suite-definitions").queryParam("x", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verify(definitionService, never()).listSummariesForUser(any());
    }
}
