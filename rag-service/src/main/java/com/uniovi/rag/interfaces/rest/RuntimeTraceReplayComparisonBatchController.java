package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.configuration.TraceComparisonBatchRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchMode;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchByConversationRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchByTraceIdsRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * P25: two POST routes delegating only to {@link RuntimeTraceReplayComparisonBatchService#execute}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayComparisonBatchController {

    private final RuntimeTraceReplayComparisonBatchService batchService;
    private final ObjectMapper strictBatchBodyMapper;

    public RuntimeTraceReplayComparisonBatchController(
            RuntimeTraceReplayComparisonBatchService batchService,
            @Qualifier(TraceComparisonBatchRestJacksonConfiguration.TRACE_COMPARISON_BATCH_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictBatchBodyMapper) {
        this.batchService = batchService;
        this.strictBatchBodyMapper = strictBatchBodyMapper;
    }

    @PostMapping(value = "/runtime-traces/replay-comparisons/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceReplayComparisonBatchResponseDto> batchByTraceIds(
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
        RuntimeTraceReplayComparisonBatchRequest domain =
                RuntimeTraceReplayComparisonBatchRequest.byTraceIds(principal.userId(), dto.traceIds());
        return respond(RuntimeTraceReplayComparisonBatchMode.BY_TRACE_IDS, batchService.execute(domain));
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/replay-comparisons/batch",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceReplayComparisonBatchResponseDto> batchByConversation(
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
        RuntimeTraceReplayComparisonBatchRequest domain =
                RuntimeTraceReplayComparisonBatchRequest.byConversation(
                        principal.userId(),
                        conversationId,
                        Optional.ofNullable(dto.createdAtFrom()),
                        Optional.ofNullable(dto.createdAtTo()),
                        workflow);
        return respond(RuntimeTraceReplayComparisonBatchMode.BY_CONVERSATION, batchService.execute(domain));
    }

    private static ResponseEntity<RuntimeTraceReplayComparisonBatchResponseDto> respond(
            RuntimeTraceReplayComparisonBatchMode mode, RuntimeTraceReplayComparisonBatchResult result) {
        if (result.batchOutcome() == RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(RuntimeTraceReplayComparisonBatchResponseDto.fromBatchResult(mode, result));
    }
}
