package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportArtifact;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportService;
import com.uniovi.rag.application.service.runtime.tracereplaybatchexport.RuntimeTraceReplayBatchExportSizeExceededException;
import com.uniovi.rag.configuration.ReplayBatchRestJacksonConfiguration;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchByConversationRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchByTraceIdsRequestDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * P29: two POST routes for replay-batch ZIP export. Delegates only to {@link RuntimeTraceReplayBatchExportService}
 * (never {@link RuntimeTraceReplayBatchService}).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayBatchExportController {

    private final RuntimeTraceReplayBatchExportService batchExportService;
    private final ObjectMapper strictBatchBodyMapper;

    public RuntimeTraceReplayBatchExportController(
            RuntimeTraceReplayBatchExportService batchExportService,
            @Qualifier(ReplayBatchRestJacksonConfiguration.REPLAY_BATCH_STRICT_OBJECT_MAPPER) ObjectMapper strictBatchBodyMapper) {
        this.batchExportService = batchExportService;
        this.strictBatchBodyMapper = strictBatchBodyMapper;
    }

    @PostMapping(value = "/runtime-traces/replays/batch/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportBatchByTraceIds(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        final RuntimeTraceReplayBatchByTraceIdsRequestDto dto;
        try {
            dto = strictBatchBodyMapper.readValue(body, RuntimeTraceReplayBatchByTraceIdsRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.traceIds() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.traceIds().size() > RuntimeTraceReplayBatchService.MAX_RAW_TRACE_IDS) {
            return ResponseEntity.badRequest().build();
        }
        for (UUID id : dto.traceIds()) {
            if (id == null) {
                return ResponseEntity.badRequest().build();
            }
        }
        try {
            RuntimeTraceReplayBatchExportArtifact artifact =
                    batchExportService.exportByTraceIds(principal.userId(), dto.traceIds());
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayBatchExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceReplayBatchExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/replays/batch/export",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportBatchByConversation(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        final RuntimeTraceReplayBatchByConversationRequestDto dto;
        try {
            dto = strictBatchBodyMapper.readValue(body, RuntimeTraceReplayBatchByConversationRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        Optional<String> workflowName = normalizeWorkflowName(dto.workflowName());
        try {
            RuntimeTraceReplayBatchExportArtifact artifact =
                    batchExportService.exportByConversation(
                            principal.userId(),
                            conversationId,
                            Optional.ofNullable(dto.createdAtFrom()),
                            Optional.ofNullable(dto.createdAtTo()),
                            workflowName);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayBatchExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceReplayBatchExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static Optional<String> normalizeWorkflowName(String workflowName) {
        if (workflowName == null) {
            return Optional.empty();
        }
        String trimmed = workflowName.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceReplayBatchExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
