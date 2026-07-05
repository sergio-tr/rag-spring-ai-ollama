package com.uniovi.rag.interfaces.rest.me;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
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

@WebMvcTest(controllers = MeEffectiveLlmDefaultsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeEffectiveLlmDefaultsController.class)
class MeEffectiveLlmDefaultsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    @MockitoBean
    private RagConfigurationResolver ragConfigurationResolver;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "user@test.local", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void effectiveDefaults_includesThinkFalseForOpenAiCompatible() throws Exception {
        when(resolvedLlmConfigResolver.resolve(eq(userId), eq(null), any()))
                .thenReturn(
                        new ResolvedLlmConfig(
                                LlmProvider.OPENAI_COMPATIBLE,
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://example",
                                "gpt",
                                "embed",
                                "OPENAI_COMPATIBLE_API_KEY",
                                null,
                                0.1,
                                60_000,
                                "",
                                Map.of()));
        when(ragConfigurationResolver.resolve(eq(userId), eq(null), any())).thenAnswer(inv -> {
            RagConfig rag = mock(RagConfig.class);
            when(rag.classifierModelId()).thenReturn("default");
            return rag;
        });

        mockMvc.perform(get(path("/me/llm/effective-defaults")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveProvider").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.chatModel").value("gpt"))
                .andExpect(jsonPath("$.classifierModelId").value("default"))
                .andExpect(jsonPath("$.temperature").value(0.1))
                .andExpect(jsonPath("$.additionalParameters.think").value(false));
    }
}

