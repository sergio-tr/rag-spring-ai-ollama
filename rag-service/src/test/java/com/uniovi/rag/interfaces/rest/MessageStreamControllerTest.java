package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MessageStreamController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MessageStreamController.class)
@ActiveProfiles("test")
class MessageStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageApplicationService chatMessageApplicationService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    private UUID conversationId;
    private UUID userId;
    private UUID jobId;
    private UUID userMsgId;
    private UUID asstId;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        userMsgId = UUID.randomUUID();
        asstId = UUID.randomUUID();
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        when(chatMessageApplicationService.enqueueMessage(eq(userId), eq(conversationId), any()))
                .thenReturn(new ChatMessageAcceptedDto(jobId, userMsgId, asstId));
        RagPrincipal testPrincipal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                testPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postMessage_returns202WithJob() throws Exception {
        mockMvc.perform(
                        post("/api/v5/conversations/{id}/messages", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"Hi there\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.pollPath").value("/api/v5/lab/jobs/" + jobId))
                .andExpect(jsonPath("$.streamPath").value("/api/v5/lab/jobs/" + jobId + "/events"));
    }
}
