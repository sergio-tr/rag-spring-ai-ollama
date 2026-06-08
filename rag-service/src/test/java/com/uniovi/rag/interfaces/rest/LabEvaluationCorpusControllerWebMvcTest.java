package com.uniovi.rag.interfaces.rest;

import static com.uniovi.rag.testsupport.RagApiTestPaths.path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexPrepareResult;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
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

    @MockitoBean
    private EvaluationCorpusReadinessService evaluationCorpusReadinessService;

    @MockitoBean
    private EvaluationCorpusIndexService evaluationCorpusIndexService;

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
    void getReadiness_returnsBlockerCode() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusReadinessService.getReadiness(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusReadinessDto(
                                corpusId,
                                UUID.randomUUID(),
                                0,
                                0,
                                0,
                                0,
                                "NO_DOCUMENTS",
                                "The knowledge base has no documents to retrieve from.",
                                null,
                                false,
                                null,
                                null,
                                List.of(),
                                false));

        mockMvc.perform(get(path("/lab/evaluation-corpora/") + corpusId + "/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryBlocker").value("NO_DOCUMENTS"))
                .andExpect(jsonPath("$.runnable").value(false));
    }

    @Test
    void prepareIndex_returnsReadiness() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusIndexService.prepareIndex(userId, corpusId))
                .thenReturn(
                        EvaluationCorpusIndexPrepareResult.built(
                                UUID.randomUUID(), UUID.randomUUID(), "hash", "profile"));
        when(evaluationCorpusReadinessService.getReadiness(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusReadinessDto(
                                corpusId,
                                UUID.randomUUID(),
                                1,
                                1,
                                0,
                                0,
                                null,
                                null,
                                UUID.randomUUID(),
                                false,
                                null,
                                null,
                                List.of(UUID.randomUUID()),
                                true));

        mockMvc.perform(post(path("/lab/evaluation-corpora/") + corpusId + "/prepare-index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runnable").value(true))
                .andExpect(jsonPath("$.activeSnapshotId").exists());
    }

    @Test
    void prepareIndex_configSnapshotUnavailable_returns422WithReasonCode() throws Exception {
        UUID corpusId = UUID.randomUUID();
        doThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE))
                .when(evaluationCorpusIndexService)
                .prepareIndex(userId, corpusId);

        mockMvc.perform(post(path("/lab/evaluation-corpora/") + corpusId + "/prepare-index"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE));
    }

    @Test
    void getCorpus_missing_returnsKbNotFoundCode() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.getSummary(userId, corpusId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, LabCorpusReasonCodes.KB_NOT_FOUND));

        mockMvc.perform(get(path("/lab/evaluation-corpora/") + corpusId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KB_NOT_FOUND"));
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
    void removeAllDocuments_returnsEmptySummary() throws Exception {
        UUID corpusId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.removeAllDocuments(userId, corpusId)).thenReturn(summary(corpusId, 0));

        mockMvc.perform(delete(path("/lab/evaluation-corpora/" + corpusId + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").value(0));
    }

    @Test
    void retryDocumentIngest_returnsSummary() throws Exception {
        UUID corpusId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(evaluationCorpusApplicationService.retryDocumentIngestion(userId, corpusId, docId))
                .thenReturn(summary(corpusId, 1));

        mockMvc.perform(post(path("/lab/evaluation-corpora/" + corpusId + "/documents/" + docId + "/retry-ingest")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").value(1));
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
