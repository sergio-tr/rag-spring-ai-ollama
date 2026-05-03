package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.KnowledgeLegacyBackfillService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminKnowledgeController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminKnowledgeController.class)
class AdminKnowledgeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeLegacyBackfillService knowledgeLegacyBackfillService;

    @Test
    void backfillLegacy_returnsUpdatedRows() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(knowledgeLegacyBackfillService.backfillProject(eq(projectId))).thenReturn(3);

        mockMvc.perform(post("/api/admin/knowledge/backfill-legacy").param("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedRows").value(3));

        verify(knowledgeLegacyBackfillService).backfillProject(projectId);
    }
}
