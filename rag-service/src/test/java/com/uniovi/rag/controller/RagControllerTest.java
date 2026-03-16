package com.uniovi.rag.controller;

import com.uniovi.rag.model.AddResult;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.observability.ObservabilitySupport;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

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

    @MockBean
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
        when(queryService.generateResponse("¿Cuántos documentos?")).thenReturn(QueryResponse.fromLLM("Hay 5 documentos.", null));

        mockMvc.perform(get("/api/v4/query").param("question", "¿Cuántos documentos?"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hay 5 documentos."));
        verify(queryService).generateResponse("¿Cuántos documentos?");
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
        when(evaluationService.evaluateWithConfiguration(any())).thenReturn(Map.of("score", 0.9));

        mockMvc.perform(post("/api/v4/evaluate/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expansion\":true,\"metadata\":false}"))
                .andExpect(status().isOk());
        verify(evaluationService).loadData();
        verify(evaluationService).evaluateWithConfiguration(any());
    }

    @Test
    void evaluateAllConfigurations_returnsOk() throws Exception {
        doNothing().when(evaluationService).loadData();
        when(evaluationService.evaluateAllConfigurations()).thenReturn(Map.of("config1", Map.of("score", 0.8)));

        mockMvc.perform(get("/api/v4/evaluate/all"))
                .andExpect(status().isOk());
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
}
