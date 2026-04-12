package com.uniovi.rag.interfaces.rest.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.me.UserMePersonalizationService;
import com.uniovi.rag.interfaces.rest.dto.me.MePersonalizationResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPersonalizationRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MePersonalizationController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MePersonalizationController.class)
class MePersonalizationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserMePersonalizationService userMePersonalizationService;

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
    void get_returnsPayload() throws Exception {
        when(userMePersonalizationService.get(eq(userId)))
                .thenReturn(new MePersonalizationResponse(1, Map.of("theme", "dark")));

        mockMvc.perform(get(RagApiTestPaths.path("/me/personalization")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.personalization.theme").value("dark"));
    }

    @Test
    void put_returnsPayload() throws Exception {
        MePutPersonalizationRequest body = new MePutPersonalizationRequest(2, Map.of("k", "v"));
        when(userMePersonalizationService.put(eq(userId), any(MePutPersonalizationRequest.class)))
                .thenReturn(new MePersonalizationResponse(2, Map.of("k", "v")));

        mockMvc.perform(
                        put(RagApiTestPaths.path("/me/personalization"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(2));
    }
}
