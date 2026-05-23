package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusAttachFromProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusCreateRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("${rag.api.product-base-path}/lab/evaluation-corpora")
public class LabEvaluationCorpusController {

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;

    public LabEvaluationCorpusController(EvaluationCorpusApplicationService evaluationCorpusApplicationService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
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

    @PostMapping(value = "/{corpusId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvaluationCorpusSummaryDto uploadDocument(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return evaluationCorpusApplicationService.uploadDocument(requireUserId(principal), corpusId, file);
    }

    @PostMapping("/{corpusId}/documents/from-project")
    public EvaluationCorpusSummaryDto attachFromProject(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID corpusId,
            @RequestBody EvaluationCorpusAttachFromProjectRequest body) {
        return evaluationCorpusApplicationService.attachFromProject(requireUserId(principal), corpusId, body);
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return principal.userId();
    }
}
