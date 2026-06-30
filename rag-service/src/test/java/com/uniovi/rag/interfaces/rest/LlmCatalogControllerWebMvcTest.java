package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LlmCatalogController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LlmCatalogController.class)
class LlmCatalogControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmCatalogApiService llmCatalogApiService;

    @Test
    void catalog_returnsConfiguredModels() throws Exception {
        when(llmCatalogApiService.listCatalog(null, null, null, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        new LlmCatalogModelDto(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "gpt-oss:20b",
                                                LlmModelCapability.CHAT,
                                                true,
                                                true,
                                                true,
                                                LlmCatalogRuntimeStatus.UNKNOWN,
                                                null,
                                                null,
                                                null,
                                                LlmCatalogSource.PROPERTIES))));

        mockMvc.perform(get(path("/llm/catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].provider").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.models[0].modelName").value("gpt-oss:20b"))
                .andExpect(jsonPath("$.models[0].capability").value("CHAT"))
                .andExpect(jsonPath("$.models[0].runtimeStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.models[0].source").value("PROPERTIES"));
    }

    @Test
    void catalog_passesQueryFilters() throws Exception {
        when(llmCatalogApiService.listCatalog(
                        eq(LlmProvider.OLLAMA_NATIVE), eq(LlmModelCapability.EMBEDDING), eq(true), eq(true)))
                .thenReturn(new LlmCatalogResponseDto(List.of()));

        mockMvc.perform(
                        get(path("/llm/catalog"))
                                .param("provider", "OLLAMA_NATIVE")
                                .param("capability", "EMBEDDING")
                                .param("selectable", "true")
                                .param("includeRuntimeStatus", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void catalog_rejectsInvalidProvider() throws Exception {
        mockMvc.perform(get(path("/llm/catalog")).param("provider", "NOT_A_PROVIDER"))
                .andExpect(status().isBadRequest());
    }
}
