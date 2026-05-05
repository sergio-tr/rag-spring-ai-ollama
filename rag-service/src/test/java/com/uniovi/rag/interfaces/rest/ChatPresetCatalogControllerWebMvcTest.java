package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.service.preset.PresetService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = ChatPresetCatalogController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ChatPresetCatalogController.class)
class ChatPresetCatalogControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresetService presetService;

    @MockitoBean
    private LabExperimentalPresetCatalogService labExperimentalPresetCatalogService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    @BeforeEach
    void setUser() {
        RagPrincipal principal = new RagPrincipal(UUID.randomUUID(), "u@test", "USER");
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
    void catalog_returnsProductAndExperimentalPresets_inSeparateSections() throws Exception {
        Instant now = Instant.now();
        RagPresetDto product =
                new RagPresetDto(
                        UUID.randomUUID(),
                        "Demo_Best",
                        null,
                        List.of("demo", "system"),
                        Map.of("useRetrieval", true),
                        true,
                        now,
                        now,
                        List.of());
        when(presetService.list(any())).thenReturn(List.of(product));

        ExperimentalPresetCatalogItemDto p6 =
                new ExperimentalPresetCatalogItemDto(
                        "cafe0001-0001-4001-8001-000000000016",
                        "P6",
                        "S2",
                        "P6 preset",
                        "desc",
                        List.of("USE_RETRIEVAL", "REASONING"),
                        false,
                        "NOT_SUPPORTED",
                        "ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED",
                        false,
                        Map.of(),
                        List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                        false,
                        true);
        when(labExperimentalPresetCatalogService.list()).thenReturn(List.of(p6));

        mockMvc.perform(get(path("/chat/presets/catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productPresets[0].name").value("Demo_Best"))
                .andExpect(jsonPath("$.experimentalPresets[0].code").value("P6"))
                .andExpect(jsonPath("$.experimentalPresets[0].supported").value(false))
                .andExpect(jsonPath("$.experimentalPresets[0].chatSelectable").value(false))
                .andExpect(jsonPath("$.experimentalPresets[0].reasonIfUnsupported")
                        .value("ADVANCED_RUNTIME_CAPABILITIES_NOT_IMPLEMENTED"));
    }
}

