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
import java.util.ArrayList;
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
        RagPresetDto p1 =
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
        RagPresetDto p2 =
                new RagPresetDto(
                        UUID.randomUUID(),
                        "Demo_Worst",
                        null,
                        List.of("demo", "system"),
                        Map.of("useRetrieval", false),
                        true,
                        now,
                        now,
                        List.of());
        RagPresetDto p3 =
                new RagPresetDto(
                        UUID.randomUUID(),
                        "Demo_NaiveFullCorpus",
                        null,
                        List.of("demo", "system"),
                        Map.of("naiveFullCorpusInPromptEnabled", true),
                        true,
                        now,
                        now,
                        List.of());
        when(presetService.list(any())).thenReturn(List.of(p1, p2, p3));

        ArrayList<ExperimentalPresetCatalogItemDto> experimental =
                new ArrayList<>(List.of(
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000010",
                                "P0",
                                "S2",
                                "P0 preset",
                                "desc",
                                List.of(),
                                true,
                                "EXECUTABLE",
                                null,
                                false,
                                Map.of(),
                                List.of("EXECUTED", "FAILED", "SKIPPED"),
                                true,
                                true),
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000014",
                                "P4",
                                "S2",
                                "P4 preset",
                                "desc",
                                List.of(),
                                true,
                                "EXECUTABLE",
                                null,
                                false,
                                Map.of(),
                                List.of("EXECUTED", "FAILED", "SKIPPED"),
                                true,
                                true),
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000016",
                                "P6",
                                "S2",
                                "P6 preset",
                                "desc",
                                List.of("USE_RETRIEVAL", "TOOLS"),
                                true,
                                "EXECUTABLE",
                                null,
                                false,
                                Map.of(),
                                List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                true,
                                true),
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000018",
                                "P8",
                                "S2",
                                "P8 preset",
                                "desc",
                                List.of("USE_RETRIEVAL", "RANKER", "POST_RETRIEVAL"),
                                true,
                                "EXECUTABLE",
                                null,
                                false,
                                Map.of(),
                                List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                true,
                                true),
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000021",
                                "P11",
                                "S3",
                                "P11 preset",
                                "desc",
                                List.of(),
                                true,
                                "REQUIRES_MULTI_TURN",
                                null,
                                true,
                                Map.of(),
                                List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                true,
                                true),
                        new ExperimentalPresetCatalogItemDto(
                                "cafe0001-0001-4001-8001-000000000022",
                                "P12",
                                "S3",
                                "P12 preset",
                                "desc",
                                List.of(),
                                true,
                                "REQUIRES_MULTI_TURN",
                                null,
                                true,
                                Map.of(),
                                List.of("EXECUTED", "NOT_SUPPORTED", "FAILED", "SKIPPED"),
                                true,
                                true)));
        // Fill up to 15 with placeholders.
        while (experimental.size() < 15) {
            int idx = experimental.size();
            experimental.add(
                    new ExperimentalPresetCatalogItemDto(
                            "cafe0001-0001-4001-8001-0000000001" + idx,
                            "PX" + idx,
                            "S2",
                            "PX" + idx + " preset",
                            "desc",
                            List.of(),
                            true,
                            "EXECUTABLE",
                            null,
                            false,
                            Map.of(),
                            List.of("EXECUTED", "FAILED", "SKIPPED"),
                            true,
                            true));
        }
        when(labExperimentalPresetCatalogService.list()).thenReturn(experimental);

        mockMvc.perform(get(path("/chat/presets/catalog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productPresets.length()").value(3))
                .andExpect(jsonPath("$.experimentalPresets.length()").value(15))
                .andExpect(jsonPath("$.experimentalPresets[?(@.code=='P11')].chatSelectable").value(true))
                .andExpect(jsonPath("$.experimentalPresets[?(@.code=='P12')].chatSelectable").value(true))
                .andExpect(jsonPath("$.experimentalPresets[?(@.code=='P6')].chatSelectable").value(true))
                .andExpect(jsonPath("$.experimentalPresets[?(@.code=='P8')].chatSelectable").value(true));
    }
}

