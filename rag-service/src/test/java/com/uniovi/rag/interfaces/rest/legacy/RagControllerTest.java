package com.uniovi.rag.interfaces.rest.legacy;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.domain.exception.DocumentAlreadyExistsException;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.application.evaluation.EvaluationCustomConfigMapper;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaProvisioningGateFilter;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RagController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = OllamaProvisioningGateFilter.class))
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RagController.class, RagApiExceptionHandler.class, EvaluationCustomConfigMapper.class})
class RagControllerTest {

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

    @MockitoBean
    private ObservabilitySupport observability;

    @BeforeEach
    void stubObservabilityToRunSupplier() {
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get())
                .when(observability).runWithSpan(any(), any(), any(), any(Supplier.class));
    }

    @Test
    void addMinute_validBody_returns201() throws Exception {
        Minute minute = new Minute(
                "min-1", "acta.pdf", "2025-01-01", null, null, null, null, null,
                null, 0, null, null, null, null, null
        );
        when(minuteDocumentRepository.addMinute(any(Minute.class))).thenReturn(AddResult.ADDED);

        mockMvc.perform(post("/api/v4/documents/minute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"min-1\",\"filename\":\"acta.pdf\",\"date\":\"2025-01-01\",\"numberOfAttendees\":0}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("min-1")));
        verify(minuteDocumentRepository).addMinute(any(Minute.class));
    }

    @Test
    void addMinute_alreadyExists_returns409() throws Exception {
        when(minuteDocumentRepository.addMinute(any(Minute.class))).thenReturn(AddResult.ALREADY_EXISTS);

        mockMvc.perform(post("/api/v4/documents/minute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"min-1\",\"filename\":\"acta.pdf\",\"date\":\"2025-01-01\",\"numberOfAttendees\":0}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already")));
    }

    @Test
    void addMinute_nullId_returns400() throws Exception {
        mockMvc.perform(post("/api/v4/documents/minute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"\",\"filename\":\"acta.pdf\",\"numberOfAttendees\":0}"))
                .andExpect(status().isBadRequest());
        verify(minuteDocumentRepository, never()).addMinute(any());
    }

    @Test
    void deleteDocumentById_validId_returns204() throws Exception {
        when(minuteDocumentRepository.deleteById("doc-1")).thenReturn(1);

        mockMvc.perform(delete("/api/v4/documents/doc-1"))
                .andExpect(status().isNoContent());
        verify(minuteDocumentRepository).deleteById("doc-1");
    }

    @Test
    void deleteDocumentById_blankId_returns400() throws Exception {
        mockMvc.perform(delete("/api/v4/documents/   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_returnsAnswerFromService() throws Exception {
        when(queryService.generateResponse(eq("¿Cuántos documentos?"), isNull()))
                .thenReturn(QueryResponse.fromLLM("Hay 5 documentos.", null));

        mockMvc.perform(get("/api/v4/query").param("question", "¿Cuántos documentos?"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("Hay 5 documentos."))
                .andExpect(jsonPath("$.error").doesNotExist());
        verify(queryService).generateResponse(org.mockito.ArgumentMatchers.eq("¿Cuántos documentos?"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void query_whenOllamaDown_returns503JsonEnvelope() throws Exception {
        when(queryService.generateResponse(eq("x"), isNull()))
                .thenThrow(RagServiceException.llmUnavailable(new java.net.ConnectException("refused")));

        mockMvc.perform(get("/api/v4/query").param("question", "x"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").isString());
    }

    @Test
    void evaluate_returnsOkWithResults() throws Exception {
        when(evaluationService.evaluate()).thenReturn(Map.of("score", 0.85));
        doNothing().when(evaluationService).loadData();

        mockMvc.perform(get("/api/v4/evaluate"))
                .andExpect(status().isOk());
        verify(evaluationService).loadData();
        verify(evaluationService).evaluate();
    }

    @Test
    void uploadDocument_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v4/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("empty")));
        verify(documentService, never()).processDocument(any());
    }

    @Test
    void clearDatabase_success_returns200() throws Exception {
        doNothing().when(documentService).clearDatabase();

        mockMvc.perform(delete("/api/v4/documents"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cleared")));
        verify(documentService).clearDatabase();
    }

    @Test
    void clearDatabase_throws_returns400() throws Exception {
        doThrow(new RuntimeException("DB error")).when(documentService).clearDatabase();

        mockMvc.perform(delete("/api/v4/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Error")));
    }

    @Test
    void evaluateWithCustomConfig_returnsOkWithResults() throws Exception {
        doNothing().when(evaluationService).loadData();
        when(evaluationService.evaluateWithConfiguration(any(), any())).thenAnswer(inv -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("score", 0.9);
            return m;
        });

        mockMvc.perform(post("/api/v4/evaluate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expansion\":true,\"metadata\":false}"))
                .andExpect(status().isOk());
        verify(evaluationService).loadData();
        verify(evaluationService).evaluateWithConfiguration(any(), any());
    }

    @Test
    void evaluateAllConfigurations_returnsOk() throws Exception {
        doNothing().when(evaluationService).loadData();
        when(evaluationService.evaluateAllConfigurations()).thenReturn(Map.of("config1", Map.of("score", 0.8)));

        mockMvc.perform(get("/api/v4/evaluate/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacyEvaluationMode").value("LEGACY_COMBINATORIAL"))
                .andExpect(jsonPath("$.configurations.config1.score").value(0.8));
        verify(evaluationService).loadData();
        verify(evaluationService).evaluateAllConfigurations();
    }

    @Test
    void uploadDocument_validFile_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        doNothing().when(documentService).processDocument(any());

        mockMvc.perform(multipart("/api/v4/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("test.pdf")));
        verify(documentService).processDocument(any());
    }

    @Test
    void uploadDocument_illegalArgument_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        doThrow(new IllegalArgumentException("bad format")).when(documentService).processDocument(any());

        mockMvc.perform(multipart("/api/v4/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bad format")));
    }

    @Test
    void uploadDocument_alreadyExists_returns409() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "dup.pdf", "application/pdf", "content".getBytes());
        doThrow(new DocumentAlreadyExistsException("dup-id")).when(documentService).processDocument(any());

        mockMvc.perform(multipart("/api/v4/documents").file(file))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dup-id")));
    }

    @Test
    void uploadDocument_genericException_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "content".getBytes());
        doThrow(new RuntimeException("disk full")).when(documentService).processDocument(any());

        mockMvc.perform(multipart("/api/v4/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("disk full")));
    }
}
