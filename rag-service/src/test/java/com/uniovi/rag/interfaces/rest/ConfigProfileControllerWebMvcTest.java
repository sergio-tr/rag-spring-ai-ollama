package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.ConfigProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ConfigProfileResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConfigProfileRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchConfigProfileRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfigProfileController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfigProfileController.class)
class ConfigProfileControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConfigProfileApplicationService configProfileApplicationService;

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
        when(configProfileApplicationService.list(eq(userId)))
                .thenReturn(
                        List.of(
                                new ConfigProfileResponseDto(
                                        pid,
                                        "METADATA",
                                        1,
                                        "L",
                                        Map.of("k", "v"),
                                        userId,
                                        false,
                                        Instant.parse("2025-01-01T00:00:00Z"))));

        mockMvc.perform(get(RagApiTestPaths.path("/config/profiles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("L"))
                .andExpect(jsonPath("$[0].id").value(pid.toString()));
    }

    @Test
    void get_returnsOk() throws Exception {
        UUID pid = UUID.randomUUID();
        when(configProfileApplicationService.get(eq(userId), eq(pid)))
                .thenReturn(
                        new ConfigProfileResponseDto(
                                pid,
                                "CHUNKING",
                                2,
                                "X",
                                Map.of(),
                                userId,
                                true,
                                Instant.parse("2025-01-01T00:00:00Z")));

        mockMvc.perform(get(RagApiTestPaths.path("/config/profiles/" + pid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileType").value("CHUNKING"));
    }

    @Test
    void create_returnsCreated() throws Exception {
        UUID pid = UUID.randomUUID();
        when(configProfileApplicationService.create(eq(userId), eq("USER"), any(CreateConfigProfileRequest.class)))
                .thenReturn(
                        new ConfigProfileResponseDto(
                                pid,
                                "INDEX",
                                1,
                                "new",
                                Map.of("a", 1),
                                userId,
                                false,
                                Instant.parse("2025-01-01T00:00:00Z")));

        CreateConfigProfileRequest body =
                new CreateConfigProfileRequest("INDEX", 1, "new", Map.of("a", 1), false);

        mockMvc.perform(
                        post(RagApiTestPaths.path("/config/profiles"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pid.toString()));
    }

    @Test
    void patch_returnsOk() throws Exception {
        UUID pid = UUID.randomUUID();
        when(configProfileApplicationService.patch(
                        eq(userId), eq("USER"), eq(pid), any(PatchConfigProfileRequest.class)))
                .thenReturn(
                        new ConfigProfileResponseDto(
                                pid,
                                "EMBEDDING",
                                1,
                                "patched",
                                Map.of("x", true),
                                userId,
                                false,
                                Instant.parse("2025-01-01T00:00:00Z")));

        PatchConfigProfileRequest body = new PatchConfigProfileRequest("patched", Map.of("x", true));

        mockMvc.perform(
                        patch(RagApiTestPaths.path("/config/profiles/" + pid))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("patched"));
    }
}
