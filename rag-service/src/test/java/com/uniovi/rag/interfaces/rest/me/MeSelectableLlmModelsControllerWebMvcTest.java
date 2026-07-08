package com.uniovi.rag.interfaces.rest.me;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.llm.catalog.MeSelectableLlmModelsService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelDto;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelsResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = MeSelectableLlmModelsController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MeSelectableLlmModelsController.class)
class MeSelectableLlmModelsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeSelectableLlmModelsService selectableLlmModelsService;

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
    void selectableModels_returnsUserScopedChatModels() throws Exception {
        when(selectableLlmModelsService.listForUser(eq(userId), eq(LlmModelCapability.CHAT)))
                .thenReturn(
                        new MeSelectableLlmModelsResponseDto(
                                LlmProvider.OPENAI_COMPATIBLE,
                                LlmModelCapability.CHAT,
                                List.of(
                                        new MeSelectableLlmModelDto(
                                                "gpt-oss:20b",
                                                "gpt-oss:20b",
                                                true,
                                                null,
                                                null,
                                                true,
                                                LlmCatalogRuntimeStatus.UNKNOWN))));

        mockMvc.perform(get(path("/me/llm/selectable-models")).param("capability", "CHAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveProvider").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.capability").value("CHAT"))
                .andExpect(jsonPath("$.models[0].modelName").value("gpt-oss:20b"))
                .andExpect(jsonPath("$.models[0].selectable").value(true));
    }

    @Test
    void selectableModels_rejectsEmbeddingCapability() throws Exception {
        when(selectableLlmModelsService.listForUser(eq(userId), eq(LlmModelCapability.EMBEDDING)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Only CHAT capability is supported for user-selectable models"));

        mockMvc.perform(get(path("/me/llm/selectable-models")).param("capability", "EMBEDDING"))
                .andExpect(status().isBadRequest());
    }
}
