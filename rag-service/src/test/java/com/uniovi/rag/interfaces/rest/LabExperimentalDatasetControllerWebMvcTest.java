package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.evaluation.workbook.ExperimentalDatasetTemplateFactory;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetLabService;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetValidationException;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssueCode;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.domain.evaluation.workbook.ValidationSeverity;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetListItemDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetQuestionCountsDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.experimental.ExperimentalDatasetValidationReportDto;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LabExperimentalDatasetController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabExperimentalDatasetController.class, ApiGlobalExceptionHandler.class})
class LabExperimentalDatasetControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExperimentalDatasetLabService experimentalDatasetLabService;

    private UUID userId;

    @BeforeEach
    void auth() {
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
    void datasetTemplate_download_returnsXlsx() throws Exception {
        byte[] bytes = ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.LLM_MODEL_BASELINE);
        when(experimentalDatasetLabService.templateBytes(ExperimentalDatasetType.LLM_MODEL_BASELINE))
                .thenReturn(bytes);

        mockMvc.perform(get(path("/lab/dataset-templates/llm-model-baseline")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("llm-model-baseline-template.xlsx")))
                .andExpect(
                        content()
                                .contentType(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void experimentalDatasetUpload_invalid_returns422WithStructuredIssues() throws Exception {
        ValidationReport report = new ValidationReport();
        report.add(
                new ValidationIssue(
                        ValidationSeverity.ERROR,
                        ValidationIssueCode.MISSING_SHEET,
                        "llm_reader_questions",
                        0,
                        "",
                        "missing"));
        when(experimentalDatasetLabService.upload(any(), any(), any(), any(), any()))
                .thenThrow(new ExperimentalDatasetValidationException(report));

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "bad.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart(path("/lab/experimental-datasets"))
                                .file(file)
                                .param("datasetType", "LLM_MODEL_BASELINE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("EXPERIMENTAL_DATASET_INVALID"))
                .andExpect(jsonPath("$.validationReport.hasErrors").value(true))
                .andExpect(jsonPath("$.validationReport.issues[0].code").value("MISSING_SHEET"));
    }

    @Test
    void experimentalDatasetUpload_valid_returns201() throws Exception {
        UUID dsId = UUID.randomUUID();
        when(experimentalDatasetLabService.upload(any(), any(), any(), any(), any()))
                .thenReturn(
                        new ExperimentalDatasetUploadResponseDto(
                                dsId,
                                "LLM_MODEL_BASELINE",
                                "LLM_ONLY",
                                "VALID",
                                2,
                                2,
                                new ExperimentalDatasetValidationReportDto(List.of(), false, false)));

        byte[] xlsx =
                ExperimentalDatasetTemplateFactory.buildTemplate(ExperimentalDatasetType.LLM_MODEL_BASELINE);
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "ok.xlsx",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        xlsx);

        mockMvc.perform(
                        multipart(path("/lab/experimental-datasets"))
                                .file(file)
                                .param("datasetType", "llm-model-baseline")
                                .param("name", "My bench"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datasetId").value(dsId.toString()))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.questionCount").value(2));
    }

    @Test
    void experimentalDatasetList_returnsArray() throws Exception {
        when(experimentalDatasetLabService.listForUser(eq(userId)))
                .thenReturn(
                        List.of(
                                new ExperimentalDatasetListItemDto(
                                        UUID.randomUUID(),
                                        "Mine",
                                        "LLM_MODEL_BASELINE",
                                        "LLM_ONLY",
                                        false,
                                        "VALID",
                                        new ExperimentalDatasetQuestionCountsDto(3, 0, 0, 0, 0),
                                        false,
                                        false,
                                        true,
                                        false,
                                        false,
                                        List.of(),
                                        Instant.parse("2026-05-01T12:00:00Z"),
                                        null)));

        mockMvc.perform(get(path("/lab/experimental-datasets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].experimentalDatasetType").value("LLM_MODEL_BASELINE"))
                .andExpect(jsonPath("$[0].readOnly").value(false))
                .andExpect(jsonPath("$[0].questionCounts.llmReaderQuestions").value(3))
                .andExpect(jsonPath("$[0].canRunLlmBaseline").value(true));
    }

    @Test
    void experimentalDatasetValidation_forbidden_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentalDatasetLabService.validationReport(eq(userId), eq(id)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dataset not owned by user"));

        mockMvc.perform(get(path("/lab/experimental-datasets/" + id + "/validation")))
                .andExpect(status().isForbidden());
    }

    @Test
    void experimentalDatasetValidation_ok_returnsReport() throws Exception {
        UUID id = UUID.randomUUID();
        when(experimentalDatasetLabService.validationReport(eq(userId), eq(id)))
                .thenReturn(new ExperimentalDatasetValidationReportDto(List.of(), false, false));

        mockMvc.perform(get(path("/lab/experimental-datasets/" + id + "/validation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasErrors").value(false))
                .andExpect(jsonPath("$.issues").isEmpty());
    }
}
