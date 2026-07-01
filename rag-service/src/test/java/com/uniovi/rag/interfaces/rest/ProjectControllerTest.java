package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.interfaces.rest.dto.ProjectListResponseDto;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.project.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.interfaces.rest.dto.CreateProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.ProjectSummaryDto;
import java.time.Instant;

@WebMvcTest(controllers = ProjectController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    private UUID userId;

    @BeforeEach
    void setUserInSecurityContext() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProjectReturnsCreatedProjectDto() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectSummaryDto created =
                new ProjectSummaryDto(
                        projectId,
                        "New project",
                        null,
                        0L,
                        0L,
                        Instant.parse("2026-07-01T12:00:00Z"),
                        null,
                        null,
                        null);
        when(projectService.create(eq(userId), org.mockito.ArgumentMatchers.any(CreateProjectRequest.class)))
                .thenReturn(created);

        mockMvc.perform(
                        post(path("/projects"))
                                .contentType("application/json")
                                .content("{\"name\":\"New project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(projectId.toString()))
                .andExpect(jsonPath("$.name").value("New project"));
    }

    @Test
    void list_returnsOk() throws Exception {
        when(projectService.list(eq(userId), eq(0), eq(24)))
                .thenReturn(new ProjectListResponseDto(List.of(), 0));

        mockMvc.perform(get(path("/projects")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
