package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConversationDocumentsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConversationDocumentsController.class)
class ConversationDocumentsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeIngestionService knowledgeIngestionService;

    private UUID userId;
    private UUID projectId;
    private UUID conversationId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
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
    void upload_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(
                        multipart(
                                        RagApiTestPaths.path(
                                                "/projects/"
                                                        + projectId
                                                        + "/conversations/"
                                                        + conversationId
                                                        + "/documents"))
                                .file(empty))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_returnsCreated() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "doc.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());
        UUID docId = UUID.randomUUID();
        when(knowledgeIngestionService.uploadConversationOverlay(
                        eq(userId), eq(projectId), eq(conversationId), any()))
                .thenReturn(
                        new ProjectDocumentDto(
                                docId,
                                "doc.txt",
                                ProjectDocumentStatus.READY,
                                1,
                                null,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                null,
                                CorpusScope.CHAT_LOCAL,
                                conversationId,
                                null,
                                null,
                                true));

        mockMvc.perform(
                        multipart(
                                        RagApiTestPaths.path(
                                                "/projects/"
                                                        + projectId
                                                        + "/conversations/"
                                                        + conversationId
                                                        + "/documents"))
                                .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.fileName").value("doc.txt"));
    }
}
