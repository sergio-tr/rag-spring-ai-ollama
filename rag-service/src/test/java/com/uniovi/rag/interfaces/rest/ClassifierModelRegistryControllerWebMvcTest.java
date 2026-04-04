package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.classifier.ClassifierModelRegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClassifierModelRegistryController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClassifierModelRegistryController.class)
class ClassifierModelRegistryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClassifierModelRegistryService classifierModelRegistryService;

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
    void list_returnsJson() throws Exception {
        UUID mid = UUID.randomUUID();
        when(classifierModelRegistryService.listForUser(userId))
                .thenReturn(
                        List.of(
                                new ClassifierModelResponseDto(
                                        mid,
                                        "n",
                                        "tag-1",
                                        "READY",
                                        Instant.parse("2025-01-01T00:00:00Z"),
                                        0.9,
                                        0.85,
                                        false,
                                        Map.of())));

        mockMvc.perform(get("/api/v5/lab/classifier/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inferenceTag").value("tag-1"));
    }

    @Test
    void activate_returnsJson() throws Exception {
        UUID mid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(classifierModelRegistryService.activateForProject(eq(userId), eq(pid), eq(mid)))
                .thenReturn(
                        new ClassifierModelResponseDto(
                                mid,
                                "n",
                                "tag-1",
                                "READY",
                                Instant.parse("2025-01-01T00:00:00Z"),
                                0.9,
                                0.85,
                                true,
                                Map.of()));

        mockMvc.perform(
                        post("/api/v5/lab/classifier/models/" + mid + "/activate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"projectId\":\"" + pid + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
