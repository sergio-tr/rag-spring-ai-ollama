package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.config.ConfigurationSchemaProvider;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConfigController.class, ConfigurationSchemaProvider.class})
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProjectConfigurationService userProjectConfigurationService;

    @MockBean
    private RuntimeConfigResolutionService runtimeConfigResolutionService;

    @Test
    void schema_returnsVersionAndFields() throws Exception {
        mockMvc.perform(get("/api/v5/config/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.fields[0].key").exists());
    }
}
