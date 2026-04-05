package com.uniovi.rag.interfaces.rest.legacy;

import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.application.evaluation.EvaluationCustomConfigMapper;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaProvisioningGateFilter;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
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
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RagController.class, RagApiExceptionHandler.class, EvaluationCustomConfigMapper.class})
class RagControllerWithoutObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private QueryService queryService;

    @MockitoBean
    private EvaluationService evaluationService;

    @MockitoBean
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
