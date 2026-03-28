package com.uniovi.rag.controller;

import com.uniovi.rag.api.OllamaConnectivityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OllamaModelControllerTest {

    private OllamaConnectivityChecker ollamaConnectivityChecker;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        OllamaModelController controller = new OllamaModelController(ollamaConnectivityChecker);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void ensureModels_withoutChatModel_preparesWithNull() throws Exception {
        when(ollamaConnectivityChecker.isOllamaReachable()).thenReturn(true);

        mockMvc.perform(post("/api/v4/ollama/models/ensure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.reachable").value(true));

        verify(ollamaConnectivityChecker).prepareForQuery(isNull());
    }

    @Test
    void ensureModels_withChatModel_passesOverride() throws Exception {
        when(ollamaConnectivityChecker.isOllamaReachable()).thenReturn(true);

        mockMvc.perform(post("/api/v4/ollama/models/ensure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatModel\":\"custom:tag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true));

        verify(ollamaConnectivityChecker).prepareForQuery("custom:tag");
    }
}
