package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.interfaces.rest.dto.AllowlistModelEntryDto;
import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.service.model.ModelsCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ModelsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ModelsController.class)
class ModelsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelsCatalogService modelsCatalogService;

    @Test
    void list_returnsCatalog() throws Exception {
        when(modelsCatalogService.buildCatalog())
                .thenReturn(new ModelsCatalogResponseDto(
                        true,
                        List.of("gemma3:4b"),
                        List.of(new AllowlistModelEntryDto("gemma3:4b", AllowedModelType.LLM, true, true))));

        mockMvc.perform(get(path("/models")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ollamaReachable").value(true))
                .andExpect(jsonPath("$.installedModelNames[0]").value("gemma3:4b"))
                .andExpect(jsonPath("$.allowlist[0].name").value("gemma3:4b"));
    }
}
