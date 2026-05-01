package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.infrastructure.classifier.ClassifierLabClient;
import com.uniovi.rag.service.evaluation.EvaluationService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LabController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LabController.class)
class LabControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationService evaluationService;

    @MockitoBean
    private RagFeatureConfiguration ragFeatureConfiguration;

    @MockitoBean
    private RagImplementationProperties ragImplementationProperties;

    @MockitoBean
    private ClassifierLabClient classifierLabClient;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

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
    void status_returnsOk_withDisabledCatalog_whenNoQuestions() throws Exception {
        when(evaluationService.getQuestionsAndAnswers()).thenReturn(Map.of());
        when(classifierLabClient.isConfigured()).thenReturn(true);

        mockMvc.perform(get(path("/lab/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.datasets.questionCount").value(0))
                .andExpect(jsonPath("$.datasets.enabled").value(false));
    }

    @Test
    void status_enablesDatasets_whenBenchmarkCatalogHasQuestions_evenIfCorpusNotPrimed() throws Exception {
        when(evaluationService.getQuestionsAndAnswers())
                .thenReturn(Map.of("What year?", "2024"));
        when(classifierLabClient.isConfigured()).thenReturn(false);

        mockMvc.perform(get(path("/lab/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets.questionCount").value(1))
                .andExpect(jsonPath("$.datasets.enabled").value(true));
    }

    @Test
    void evaluateRag_sync_returnsBodyFromService() throws Exception {
        when(evaluationService.evaluate()).thenReturn(Map.of("evaluation_summary", Map.of()));

        mockMvc.perform(post(path("/lab/evaluations/rag")).param("sync", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation_summary").exists());
    }

    @Test
    void evaluateRag_async_returnsAccepted() throws Exception {
        UUID job = UUID.randomUUID();
        when(asyncTaskService.submitEvalRag(eq(userId), isNull())).thenReturn(job);

        mockMvc.perform(post(path("/lab/evaluations/rag")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(job.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(asyncTaskService).submitEvalRag(eq(userId), isNull());
    }

    @Test
    void evaluateLlm_sync_delegatesWithCustomConfig() throws Exception {
        when(ragFeatureConfiguration.isExpansionEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isNerEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isToolsEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isMetadataEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isReasoningEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isRankerEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isPostRetrievalEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isFunctionCallingEnabled()).thenReturn(false);
        when(ragFeatureConfiguration.isUseRetrieval()).thenReturn(true);
        when(ragFeatureConfiguration.isUseAdvisor()).thenReturn(true);
        when(evaluationService.evaluateWithConfiguration(any(), any())).thenReturn(Map.of("mode", "llm"));

        mockMvc.perform(post(path("/lab/evaluations/llm")).param("sync", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("llm"));
    }
}
