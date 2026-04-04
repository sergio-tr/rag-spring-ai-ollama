package com.uniovi.rag.interfaces.rest.legacy;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OllamaModelController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OllamaModelController.class)
@TestPropertySource(
        properties = {
            "rag.api.product-base-path=/api/v5",
            "rag.api.legacy-base-path=/api/v4"
        })
class OllamaModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OllamaConnectivityChecker ollamaConnectivityChecker;

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
