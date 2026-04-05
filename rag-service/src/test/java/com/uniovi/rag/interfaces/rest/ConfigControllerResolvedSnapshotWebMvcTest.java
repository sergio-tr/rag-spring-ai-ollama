package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.config.ConfigurationSchemaProvider;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.RuntimeConfigResolutionService;
import com.uniovi.rag.interfaces.rest.dto.CreateResolvedConfigSnapshotRequest;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotCreatedResponse;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotResponse;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.config.UserProjectConfigurationService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConfigController.class, ConfigurationSchemaProvider.class})
class ConfigControllerResolvedSnapshotWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProjectConfigurationService userProjectConfigurationService;

    @MockitoBean
    private RuntimeConfigResolutionService runtimeConfigResolutionService;

    @MockitoBean
    private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

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
    void postResolvedSnapshot_returnsCreated() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        when(resolvedConfigSnapshotApplicationService.createFromRequest(eq(userId), any(CreateResolvedConfigSnapshotRequest.class)))
                .thenReturn(new ResolvedConfigSnapshotCreatedResponse(snapId, "h1", Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(
                        post("/api/v5/config/resolved-snapshots")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreateResolvedConfigSnapshotRequest(
                                                        projectId, null, null, null, null, null, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(snapId.toString()))
                .andExpect(jsonPath("$.configHash").value("h1"));
    }

    @Test
    void getResolvedSnapshot_returnsOk() throws Exception {
        UUID snapId = UUID.randomUUID();
        when(resolvedConfigSnapshotApplicationService.getByIdForUser(userId, snapId))
                .thenReturn(
                        new ResolvedConfigSnapshotResponse(
                                snapId,
                                Instant.parse("2026-01-01T00:00:00Z"),
                                java.util.Map.of(),
                                java.util.Map.of(),
                                java.util.Map.of(),
                                java.util.Map.of(),
                                java.util.Map.of(),
                                "x",
                                java.util.Map.of(),
                                "h",
                                null,
                                null,
                                null));

        mockMvc.perform(get("/api/v5/config/resolved-snapshots/" + snapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(snapId.toString()))
                .andExpect(jsonPath("$.configHash").value("h"));
    }
}
