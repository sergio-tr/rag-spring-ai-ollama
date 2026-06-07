package com.uniovi.rag.interfaces.rest;


import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleCounts;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.application.service.classifier.ClassifierModelRegistryService;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.infrastructure.classifier.ClassifierLabClient;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private ClassifierLabClient classifierLabClient;

    @MockitoBean
    private ClassifierModelRegistryService classifierModelRegistryService;

    @MockitoBean
    private AsyncTaskService asyncTaskService;

    @MockitoBean
    private RagApiPathProperties apiPathProperties;

    @MockitoBean
    private EvaluationReferenceBundleLoader referenceBundleLoader;
    @MockitoBean
    private LabExperimentalPresetCatalogService experimentalPresetCatalogService;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(apiPathProperties.getProductBasePath()).thenReturn("/api/v5");
        when(referenceBundleLoader.getSnapshot()).thenReturn(ReferenceBundleSnapshot.classpathMissing());
        when(experimentalPresetCatalogService.list()).thenReturn(List.of());
        when(classifierLabClient.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void status_returnsOk_whenReferenceBundleAbsent_datasetKindsNotReady() throws Exception {
        mockMvc.perform(get(path("/lab/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Research Lab is ready")))
                .andExpect(jsonPath("$.message").value(not(containsString("POST"))))
                .andExpect(jsonPath("$.referenceBundleAvailable").value(false))
                .andExpect(jsonPath("$.referenceBundleValid").value(false))
                .andExpect(jsonPath("$.datasetKindsReady").value(false))
                .andExpect(jsonPath("$.datasets.enabled").value(false))
                .andExpect(jsonPath("$.datasets.datasetKindsReady").value(false))
                .andExpect(jsonPath("$.countsByDatasetKind.llmReaderQuestions").value(0));
    }

    @Test
    void status_datasetKindsReady_whenBundleValidAndCountsPositive() throws Exception {
        EvaluationWorkbook wb = EvaluationWorkbook.builder().build();
        ReferenceBundleCounts counts = new ReferenceBundleCounts(3, 2, 4, 0, 0, 0, 0);
        ReferenceBundleSnapshot snap =
                new ReferenceBundleSnapshot(true, wb, new ValidationReport(), counts, Optional.of("test-pv"), Optional.of("abc"), 123);
        when(referenceBundleLoader.getSnapshot()).thenReturn(snap);

        mockMvc.perform(get(path("/lab/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKindsReady").value(true))
                .andExpect(jsonPath("$.datasets.enabled").value(true))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.protocolVersion").value("test-pv"));
    }

    @Test
    void status_includesValidationIssues_whenReportHasEntries() throws Exception {
        EvaluationWorkbook wb = EvaluationWorkbook.builder().build();
        ValidationReport report = new ValidationReport();
        report.add(
                new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.WORKBOOK_IO_ERROR,
                        "sheet",
                        1,
                        "",
                        "broken"));
        ReferenceBundleSnapshot snap =
                new ReferenceBundleSnapshot(true, wb, report, ReferenceBundleCounts.empty(), Optional.empty(), Optional.empty(), 0);
        when(referenceBundleLoader.getSnapshot()).thenReturn(snap);

        mockMvc.perform(get(path("/lab/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceBundleValid").value(false))
                .andExpect(jsonPath("$.datasetKindsReady").value(false))
                .andExpect(jsonPath("$.validationStatus").value("INVALID"))
                .andExpect(jsonPath("$.validationIssues[0].code").value("WORKBOOK_IO_ERROR"));
    }
}
