package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportArtifact;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportNotAttemptedException;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportService;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatchexport.RuntimeTraceReplayComparisonBatchExportSizeExceededException;
import com.uniovi.rag.configuration.TraceComparisonBatchRestJacksonConfiguration;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchByConversationRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchByTraceIdsRequestDto;
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
 * P26: two POST routes for batch replay-comparison ZIP export. Delegates only to
 * {@link RuntimeTraceReplayComparisonBatchExportService} (never {@link RuntimeTraceReplayComparisonBatchService}).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayComparisonBatchExportController {

    private final RuntimeTraceReplayComparisonBatchExportService batchExportService;
    private final ObjectMapper strictBatchBodyMapper;

    public RuntimeTraceReplayComparisonBatchExportController(
            RuntimeTraceReplayComparisonBatchExportService batchExportService,
            @Qualifier(TraceComparisonBatchRestJacksonConfiguration.TRACE_COMPARISON_BATCH_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictBatchBodyMapper) {
        this.batchExportService = batchExportService;
        this.strictBatchBodyMapper = strictBatchBodyMapper;
    }

    @PostMapping(
            value = "/runtime-traces/replay-comparisons/batch/export",
            consumes = MediaType.APPLICATION_JSON_VALUE)
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
        final RuntimeTraceReplayComparisonBatchByTraceIdsRequestDto dto;
        try {
            dto = strictBatchBodyMapper.readValue(body, RuntimeTraceReplayComparisonBatchByTraceIdsRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.traceIds() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.traceIds().size() > RuntimeTraceReplayComparisonBatchService.MAX_RAW_TRACE_IDS) {
            return ResponseEntity.badRequest().build();
        }
        for (UUID id : dto.traceIds()) {
            if (id == null) {
                return ResponseEntity.badRequest().build();
            }
        }
        try {
            RuntimeTraceReplayComparisonBatchExportArtifact artifact =
                    batchExportService.exportByTraceIds(principal.userId(), dto.traceIds());
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayComparisonBatchExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceReplayComparisonBatchExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/replay-comparisons/batch/export",
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
        final RuntimeTraceReplayComparisonBatchByConversationRequestDto dto;
        try {
            dto = strictBatchBodyMapper.readValue(body, RuntimeTraceReplayComparisonBatchByConversationRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        Optional<String> workflow =
                Optional.ofNullable(dto.workflowName()).map(String::trim).filter(s -> !s.isEmpty());
        try {
            RuntimeTraceReplayComparisonBatchExportArtifact artifact =
                    batchExportService.exportByConversation(
                            principal.userId(),
                            conversationId,
                            Optional.ofNullable(dto.createdAtFrom()),
                            Optional.ofNullable(dto.createdAtTo()),
                            workflow);
            return toZipResponse(artifact);
        } catch (RuntimeTraceReplayComparisonBatchExportSizeExceededException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Export too large");
        } catch (RuntimeTraceReplayComparisonBatchExportNotAttemptedException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static ResponseEntity<byte[]> toZipResponse(RuntimeTraceReplayComparisonBatchExportArtifact artifact) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(artifact.sizeBytes()))
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .body(artifact.content());
    }
}
