package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.knowledge.ProjectKnowledgeApplicationService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectKnowledgeController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ProjectKnowledgeController.class})
class ProjectKnowledgeControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KnowledgeIngestionService knowledgeIngestionService;
    @MockitoBean private ProjectKnowledgeApplicationService projectKnowledgeApplicationService;

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
    void ingest_whenFileMissingOrEmpty_returns400() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile empty = new MockMultipartFile("file", "x.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(
                        multipart(path("/projects/") + projectId + "/knowledge/ingest")
                                .file(empty)
                                .param("corpusScope", CorpusScope.PROJECT_SHARED.name()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_chatLocalWithoutConversationId_returns400() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());

        mockMvc.perform(
                        multipart(path("/projects/") + projectId + "/knowledge/ingest")
                                .file(f)
                                .param("corpusScope", CorpusScope.CHAT_LOCAL.name()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_projectSharedWithConversationId_returns400() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());

        mockMvc.perform(
                        multipart(path("/projects/") + projectId + "/knowledge/ingest")
                                .file(f)
                                .param("corpusScope", CorpusScope.PROJECT_SHARED.name())
                                .param("conversationId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_chatLocalWithConversationId_uploadsOverlay_andReturns201() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        MockMultipartFile f = new MockMultipartFile("file", "x.txt", MediaType.TEXT_PLAIN_VALUE, "hi".getBytes());

        when(knowledgeIngestionService.uploadConversationOverlay(eq(userId), eq(projectId), eq(conversationId), any()))
                .thenReturn(
                        new ProjectDocumentDto(
                                docId,
                                "x.txt",
                                ProjectDocumentStatus.READY,
                                1,
                                null,
                                Instant.parse("2026-01-01T00:00:00Z"),
                                null,
                                CorpusScope.CHAT_LOCAL,
                                conversationId,
                                null,
                                "h",
                                true));

        mockMvc.perform(
                        multipart(path("/projects/") + projectId + "/knowledge/ingest")
                                .file(f)
                                .param("corpusScope", CorpusScope.CHAT_LOCAL.name())
                                .param("conversationId", conversationId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.corpusScope").value(CorpusScope.CHAT_LOCAL.name()));

        verify(knowledgeIngestionService).uploadConversationOverlay(eq(userId), eq(projectId), eq(conversationId), any());
    }
}
