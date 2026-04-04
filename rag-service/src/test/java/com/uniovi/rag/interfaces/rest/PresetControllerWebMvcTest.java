package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.preset.PresetService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresetController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PresetController.class)
class PresetControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresetService presetService;

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
    void list_returnsOk() throws Exception {
        UUID pid = UUID.randomUUID();
        when(presetService.list(eq(userId)))
                .thenReturn(
                        List.of(new RagPresetDto(
                                pid,
                                "Default",
                                "d",
                                List.of("t"),
                                Map.of("topK", 5),
                                false,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v5/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Default"))
                .andExpect(jsonPath("$[0].id").value(pid.toString()));
    }

    @Test
    void create_returnsCreated() throws Exception {
        UUID pid = UUID.randomUUID();
        when(presetService.create(eq(userId), any()))
                .thenReturn(
                        new RagPresetDto(
                                pid,
                                "P",
                                null,
                                List.of(),
                                Map.of(),
                                false,
                                Instant.parse("2025-01-01T00:00:00Z"),
                                Instant.parse("2025-01-01T00:00:00Z")));

        mockMvc.perform(
                        post("/api/v5/presets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"P\",\"values\":{\"topK\":3}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("P"));
    }
}
