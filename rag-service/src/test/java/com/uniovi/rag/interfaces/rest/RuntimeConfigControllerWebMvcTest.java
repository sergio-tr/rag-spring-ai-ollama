package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigCapabilitiesService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(controllers = RuntimeConfigController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RuntimeConfigController.class)
class RuntimeConfigControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RuntimeConfigCapabilitiesService capabilitiesService;
    @MockitoBean private RuntimeConfigValidationService validationService;

    @BeforeEach
    void setUser() {
        RagPrincipal principal = new RagPrincipal(UUID.randomUUID(), "u@test", "USER");
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
    void capabilities_returnsMatrix() throws Exception {
        when(capabilitiesService.getCapabilities())
                .thenReturn(
                        new RuntimeConfigCapabilitiesResponse(
                                List.of(
                                        new RuntimeConfigCapabilityDto(
                                                "useRetrieval",
                                                "Use retrieval",
                                                "desc",
                                                "Retrieval",
                                                true,
                                                true,
                                                List.of(),
                                                List.of(),
                                                null,
                                                Map.of()))));

        mockMvc.perform(get(path("/runtime-config/capabilities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities[0].key").value("useRetrieval"));
    }

    @Test
    void validate_returnsStructuredResponse() throws Exception {
        when(validationService.validate(any(), any()))
                .thenReturn(
                        new RuntimeConfigValidateResponse(
                                false,
                                false,
                                Map.of("useRetrieval", true),
                                List.of(new RuntimeConfigValidationIssueDto("X", "field", "msg", "ERROR")),
                                List.of(),
                                "ChunkDenseRagWorkflow"));

        RuntimeConfigValidateRequest body =
                new RuntimeConfigValidateRequest(UUID.randomUUID(), null, null, Map.of("useRetrieval", true));

        mockMvc.perform(
                        post(path("/runtime-config/validate"))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("X"));
    }
}

