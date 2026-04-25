package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.knowledge.ProjectKnowledgeApplicationService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotSummaryResponse;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.RagApiTestPaths;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectKnowledgeController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProjectKnowledgeController.class)
class ProjectKnowledgeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeIngestionService knowledgeIngestionService;

    @MockitoBean
    private ProjectKnowledgeApplicationService projectKnowledgeApplicationService;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listSnapshots_returnsJson() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        Instant created = Instant.parse("2026-02-01T10:00:00Z");
        when(projectKnowledgeApplicationService.listSnapshots(
                        eq(userId), eq(projectId), eq(CorpusScope.PROJECT_SHARED), eq(null)))
                .thenReturn(
                        List.of(
                                new KnowledgeSnapshotSummaryResponse(
                                        snapId,
                                        "hash",
                                        KnowledgeSnapshotScopeType.PROJECT,
                                        IndexSnapshotStatus.ACTIVE,
                                        created,
                                        null)));

        mockMvc.perform(
                        get(RagApiTestPaths.path("/projects/" + projectId + "/knowledge/snapshots"))
                                .param("corpusScope", "PROJECT_SHARED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(snapId.toString()))
                .andExpect(jsonPath("$[0].signatureHash").value("hash"));
    }

    @Test
    void reindex_returnsNoContent() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(
                        post(RagApiTestPaths.path("/projects/" + projectId + "/knowledge/reindex"))
                                .param("corpusScope", "PROJECT_SHARED"))
                .andExpect(status().isNoContent());

        verify(projectKnowledgeApplicationService)
                .triggerReindex(eq(userId), eq(projectId), eq(CorpusScope.PROJECT_SHARED), eq(null));
    }
}
