package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.ChatMessageApplicationService;
import com.uniovi.rag.application.service.ConversationApplicationService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.dto.ChatMessageAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.PatchUserMessageRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConversationController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConversationController.class})
class ConversationControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ConversationApplicationService conversationApplicationService;
    @MockitoBean private ChatMessageApplicationService chatMessageApplicationService;
    @MockitoBean private RagApiPathProperties apiPathProperties;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patchUserMessage_whenBlankContent_returns400() throws Exception {
        UUID conv = UUID.randomUUID();
        UUID msg = UUID.randomUUID();

        mockMvc.perform(
                        patch(path("/conversations/") + conv + "/messages/" + msg)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new PatchUserMessageRequest("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryAssistant_returnsAcceptedWithJobLinks() throws Exception {
        UUID conv = UUID.randomUUID();
        UUID assistantMsg = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        when(chatMessageApplicationService.retryAssistantMessage(userId, conv, assistantMsg))
                .thenReturn(new ChatMessageAcceptedDto(jobId, null, null));

        mockMvc.perform(post(path("/conversations/") + conv + "/messages/" + assistantMsg + "/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.pollPath").value("/api/v5/lab/jobs/" + jobId))
                .andExpect(jsonPath("$.streamPath").value("/api/v5/lab/jobs/" + jobId + "/events"));

        verify(chatMessageApplicationService).retryAssistantMessage(userId, conv, assistantMsg);
    }
}
