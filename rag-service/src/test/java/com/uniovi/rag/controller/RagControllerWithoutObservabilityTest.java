package com.uniovi.rag.controller;

import com.uniovi.rag.api.RagApiExceptionHandler;
import com.uniovi.rag.exception.RagServiceException;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.ollama.OllamaProvisioningGateFilter;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slices {@link RagController} without an {@code ObservabilitySupport} bean so branches with
 * {@code observability == null} are exercised.
 */
@WebMvcTest(controllers = RagController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = OllamaProvisioningGateFilter.class))
@Import(RagApiExceptionHandler.class)
class RagControllerWithoutObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private QueryService queryService;

    @MockBean
    private EvaluationService evaluationService;

    @MockBean
    private MinuteDocumentRepository minuteDocumentRepository;

    @Test
    void query_withoutObservabilityBean_returnsJson() throws Exception {
        when(queryService.generateResponse(eq("hello"), isNull())).thenReturn(QueryResponse.fromLLM("ok", null));

        mockMvc.perform(get("/api/v4/query").param("question", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("ok"));
    }

    @Test
    void query_withoutObservability_oLLMError_returns503() throws Exception {
        when(queryService.generateResponse(anyString(), isNull())).thenThrow(RagServiceException.llmUnavailable(new java.net.ConnectException()));

        mockMvc.perform(get("/api/v4/query").param("question", "x"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
