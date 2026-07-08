package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusGoldAlignmentService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexPrepareResult;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusIndexService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusGoldAlignmentDto;
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
    private final EvaluationCorpusIndexService evaluationCorpusIndexService;
    private final EvaluationCorpusGoldAlignmentService evaluationCorpusGoldAlignmentService;

    public LabEvaluationCorpusController(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            EvaluationCorpusReadinessService evaluationCorpusReadinessService,
            EvaluationCorpusIndexService evaluationCorpusIndexService,
            EvaluationCorpusGoldAlignmentService evaluationCorpusGoldAlignmentService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.evaluationCorpusReadinessService = evaluationCorpusReadinessService;
        this.evaluationCorpusIndexService = evaluationCorpusIndexService;
        this.evaluationCorpusGoldAlignmentService = evaluationCorpusGoldAlignmentService;
    }

    @PostMapping("/align-from-reference-bundle")
    public EvaluationCorpusGoldAlignmentDto alignFromReferenceBundle(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "replaceExisting", defaultValue = "true") boolean replaceExisting) {
        return evaluationCorpusGoldAlignmentService.alignFromReferenceBundle(requireUserId(principal), replaceExisting);
    }

    @GetMapping("/align-from-reference-bundle/preview")
    public EvaluationCorpusGoldAlignmentDto previewGoldAlignment(@AuthenticationPrincipal RagPrincipal principal) {
        return evaluationCorpusGoldAlignmentService.previewAlignment(requireUserId(principal));
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

    @PostMapping("/{corpusId}/prepare-index")
    public EvaluationCorpusReadinessDto prepareIndex(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestParam(name = "embeddingModelId", required = false) String embeddingModelId,
            @RequestParam(name = "presetGroupKey", required = false) String presetGroupKey) {
        UUID userId = requireUserId(principal);
        EvaluationCorpusIndexPrepareResult prepareResult;
        LabPresetRunGroupKey groupKey = parsePresetGroupKey(presetGroupKey);
        if (groupKey == LabPresetRunGroupKey.HYBRID_METADATA) {
            prepareResult =
                    evaluationCorpusIndexService.prepareForPresetRequirements(
                            userId,
                            corpusId,
                            LabPresetRunGroupKey.HYBRID_METADATA,
                            new ExperimentalPresetCanonicalCatalog.IndexRequirements(
                                    ExperimentalPresetCanonicalCatalog.RequiredMaterialization.HYBRID, true),
                            embeddingModelId != null && !embeddingModelId.isBlank() ? embeddingModelId.trim() : null,
                            true);
        } else if (embeddingModelId != null && !embeddingModelId.isBlank()) {
            prepareResult =
                    evaluationCorpusIndexService.prepareForPresetRequirements(
                            userId,
                            corpusId,
                            LabPresetRunGroupKey.CHUNK_LEVEL,
                            ExperimentalPresetCanonicalCatalog.IndexRequirements.none(),
                            embeddingModelId.trim(),
                            true);
        } else {
            prepareResult = evaluationCorpusIndexService.prepareDefaultIndex(userId, corpusId);
        }
        if (!prepareResult.succeeded()) {
            String code =
                    prepareResult.reasonCode() != null
                            ? prepareResult.reasonCode()
                            : LabCorpusReasonCodes.REINDEX_REQUIRED;
            HttpStatus status =
                    LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE.equals(code)
                            ? HttpStatus.UNPROCESSABLE_ENTITY
                            : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, code);
        }
        EvaluationCorpusReadinessDto readiness = evaluationCorpusReadinessService.getReadiness(userId, corpusId);
        UUID preparedSnapshotId = prepareResult.knowledgeIndexSnapshotId();
        if (preparedSnapshotId == null) {
            return readiness;
        }
        return new EvaluationCorpusReadinessDto(
                readiness.corpusId(),
                readiness.indexProjectId(),
                readiness.documentCount(),
                readiness.readyCount(),
                readiness.storageReadyCount(),
                readiness.processingCount(),
                readiness.failedCount(),
                readiness.primaryBlocker(),
                readiness.primaryBlockerMessage(),
                preparedSnapshotId,
                readiness.reindexRequired(),
                readiness.snapshotBlocker(),
                readiness.snapshotBlockerDetailCode(),
                List.of(preparedSnapshotId),
                readiness.runnable());
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

    private static LabPresetRunGroupKey parsePresetGroupKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LabPresetRunGroupKey.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid presetGroupKey");
        }
    }
}
