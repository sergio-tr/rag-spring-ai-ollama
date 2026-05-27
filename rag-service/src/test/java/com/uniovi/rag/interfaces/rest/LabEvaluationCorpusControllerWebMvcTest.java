package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusDocumentUploadItemDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusDocumentsUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LabEvaluationCorpusController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LabEvaluationCorpusController.class, ApiGlobalExceptionHandler.class})
class LabEvaluationCorpusControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationCorpusApplicationService evaluationCorpusApplicationService;

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
    void createCorpus_returns201() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.create(eq(userId), any()))
                .thenReturn(summary(corpusId));

        mockMvc.perform(post(path("/lab/evaluation-corpora")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(corpusId.toString()));
    }

    @Test
    void getCorpus_returnsSummary() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.getSummary(userId, corpusId)).thenReturn(summary(corpusId));

        mockMvc.perform(get(path("/lab/evaluation-corpora/") + corpusId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").value(0));
    }

    @Test
    void uploadDocuments_acceptsRequestParamFilePart() throws Exception {
        UUID corpusId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        EvaluationCorpusDocumentsUploadResponseDto response =
                new EvaluationCorpusDocumentsUploadResponseDto(
                        summary(corpusId, 1),
                        List.of(new EvaluationCorpusDocumentUploadItemDto(docId, "a.pdf", "PROCESSING", null)));
        when(evaluationCorpusApplicationService.uploadDocuments(eq(userId), eq(corpusId), any()))
                .thenReturn(response);

        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "a.pdf", "application/pdf", "bytes".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(path("/lab/evaluation-corpora/" + corpusId + "/documents")).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpus.documentCount").value(1))
                .andExpect(jsonPath("$.uploads[0].fileName").value("a.pdf"))
                .andExpect(jsonPath("$.uploads[0].status").value("PROCESSING"));
    }

    @Test
    void uploadDocuments_aliasLabCorporaPath() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.uploadDocuments(eq(userId), eq(corpusId), any()))
                .thenReturn(new EvaluationCorpusDocumentsUploadResponseDto(summary(corpusId, 0), List.of()));

        MockMultipartFile file =
                new MockMultipartFile(
                        "files", "b.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(path("/lab/corpora/" + corpusId + "/documents")).file(file))
                .andExpect(status().isOk());
    }

    private static EvaluationCorpusSummaryDto summary(UUID corpusId) {
        return summary(corpusId, 0);
    }

    private static EvaluationCorpusSummaryDto summary(UUID corpusId, int documentCount) {
        return new EvaluationCorpusSummaryDto(
                corpusId,
                "Lab evaluation corpus",
                "UPLOADED",
                documentCount,
                0,
                0,
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
