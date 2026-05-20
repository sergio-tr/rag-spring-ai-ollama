package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectIndexProfileDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

@WebMvcTest(controllers = ProjectIndexProfileController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProjectIndexProfileController.class)
class ProjectIndexProfileControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProjectIndexProfileApplicationService applicationService;

    private UUID uid;

    @BeforeEach
    void setUser() {
        uid = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(uid, "u@test", "USER");
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
    void get_returnsDefaultProfile() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now();
        when(applicationService.get(eq(uid), eq(projectId)))
                .thenReturn(
                        new ProjectIndexProfileDto(
                                projectId,
                                "CHUNK_LEVEL",
                                false,
                                null,
                                "mxbai-embed-large",
                                400,
                                null,
                                "hash",
                                now,
                                now));

        mockMvc.perform(get(path("/projects/" + projectId + "/index-profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.materializationStrategy").value("CHUNK_LEVEL"))
                .andExpect(jsonPath("$.profileHash").value("hash"));
    }
}

