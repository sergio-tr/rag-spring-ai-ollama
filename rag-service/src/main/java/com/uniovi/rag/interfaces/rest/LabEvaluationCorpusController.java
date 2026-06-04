package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusAttachFromProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusCreateRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusDocumentsUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({
    "${rag.api.product-base-path}/lab/evaluation-corpora",
    "${rag.api.product-base-path}/lab/corpora"
})
public class LabEvaluationCorpusController {

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final EvaluationCorpusReadinessService evaluationCorpusReadinessService;

    public LabEvaluationCorpusController(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            EvaluationCorpusReadinessService evaluationCorpusReadinessService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.evaluationCorpusReadinessService = evaluationCorpusReadinessService;
    }

    @PostMapping
    public ResponseEntity<EvaluationCorpusSummaryDto> create(
            @AuthenticationPrincipal RagPrincipal principal, @RequestBody(required = false) EvaluationCorpusCreateRequest body) {
        EvaluationCorpusSummaryDto created =
                evaluationCorpusApplicationService.create(requireUserId(principal), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{corpusId}")
    public EvaluationCorpusSummaryDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID corpusId) {
        return evaluationCorpusApplicationService.getSummary(requireUserId(principal), corpusId);
    }

    @GetMapping("/{corpusId}/readiness")
    public EvaluationCorpusReadinessDto getReadiness(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID corpusId) {
        return evaluationCorpusReadinessService.getReadiness(requireUserId(principal), corpusId);
    }

    /**
     * Multipart upload for one or more evaluation corpus documents ({@code file} or {@code files} parts).
     * Uses {@link RequestParam} so browser {@code FormData} uploads bind reliably.
     */
    @PostMapping(value = "/{corpusId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationCorpusDocumentsUploadResponseDto uploadDocuments(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile)
            throws IOException {
        return evaluationCorpusApplicationService.uploadDocuments(
                requireUserId(principal), corpusId, resolveMultipartFiles(files, singleFile));
    }

    /** @deprecated Prefer {@link #uploadDocuments}; kept for older clients. */
    @PostMapping(value = "/{corpusId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationCorpusDocumentsUploadResponseDto uploadDocumentLegacy(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile)
            throws IOException {
        return uploadDocuments(principal, corpusId, files, singleFile);
    }

    @DeleteMapping("/{corpusId}/documents/{documentId}")
    public EvaluationCorpusSummaryDto removeDocument(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @PathVariable UUID documentId) {
        return evaluationCorpusApplicationService.removeDocument(requireUserId(principal), corpusId, documentId);
    }

    @DeleteMapping("/{corpusId}/documents")
    public EvaluationCorpusSummaryDto removeAllDocuments(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID corpusId) {
        return evaluationCorpusApplicationService.removeAllDocuments(requireUserId(principal), corpusId);
    }

    @PostMapping("/{corpusId}/documents/{documentId}/retry-ingest")
    public EvaluationCorpusSummaryDto retryDocumentIngestion(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @PathVariable UUID documentId) {
        return evaluationCorpusApplicationService.retryDocumentIngestion(
                requireUserId(principal), corpusId, documentId);
    }

    @PostMapping("/{corpusId}/documents/from-project")
    public EvaluationCorpusSummaryDto attachFromProject(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestBody EvaluationCorpusAttachFromProjectRequest body) {
        return evaluationCorpusApplicationService.attachFromProject(requireUserId(principal), corpusId, body);
    }

    private static List<MultipartFile> resolveMultipartFiles(List<MultipartFile> files, MultipartFile singleFile) {
        List<MultipartFile> resolved = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    resolved.add(file);
                }
            }
        }
        if (singleFile != null && !singleFile.isEmpty()) {
            resolved.add(singleFile);
        }
        return resolved;
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
