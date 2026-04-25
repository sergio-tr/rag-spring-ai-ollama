package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.MeDocumentQueryService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.me.MeDocumentsPageResponse;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MeDocumentsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeDocumentsController.class)
class MeDocumentsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeDocumentQueryService meDocumentQueryService;

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
    void list_defaultsAndFilters() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(meDocumentQueryService.list(
                        eq(userId),
                        eq(0),
                        eq(20),
                        eq(CorpusScope.PROJECT_SHARED),
                        eq(projectId),
                        isNull(),
                        eq(ProjectDocumentStatus.READY)))
                .thenReturn(new MeDocumentsPageResponse(List.of(), 0L));

        mockMvc.perform(
                        get(RagApiTestPaths.path("/me/documents"))
                                .param("corpusScope", "PROJECT_SHARED")
                                .param("projectId", projectId.toString())
                                .param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void list_invalidCorpusScope_returnsBadRequest() throws Exception {
        mockMvc.perform(get(RagApiTestPaths.path("/me/documents")).param("corpusScope", "INVALID"))
                .andExpect(status().isBadRequest());
    }
}
