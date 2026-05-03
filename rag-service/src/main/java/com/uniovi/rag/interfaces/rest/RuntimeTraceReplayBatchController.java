package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.tracereplaybatch.RuntimeTraceReplayBatchService;
import com.uniovi.rag.configuration.ReplayBatchRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchMode;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchByConversationRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchByTraceIdsRequestDto;
import com.uniovi.rag.interfaces.rest.dto.tracereplaybatch.RuntimeTraceReplayBatchResponseDto;
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
 * P28: two POST routes delegating only to {@link RuntimeTraceReplayBatchService#execute}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceReplayBatchController {

    private final RuntimeTraceReplayBatchService batchService;
    private final ObjectMapper strictBatchBodyMapper;

    public RuntimeTraceReplayBatchController(
            RuntimeTraceReplayBatchService batchService,
            @Qualifier(ReplayBatchRestJacksonConfiguration.REPLAY_BATCH_STRICT_OBJECT_MAPPER) ObjectMapper strictBatchBodyMapper) {
        this.batchService = batchService;
        this.strictBatchBodyMapper = strictBatchBodyMapper;
    }

    @PostMapping(value = "/runtime-traces/replays/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceReplayBatchResponseDto> batchByTraceIds(
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
        RuntimeTraceReplayBatchRequest domain = RuntimeTraceReplayBatchRequest.byTraceIds(principal.userId(), dto.traceIds());
        return respond(RuntimeTraceReplayBatchMode.BY_TRACE_IDS, batchService.execute(domain));
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/replays/batch",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceReplayBatchResponseDto> batchByConversation(
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
        RuntimeTraceReplayBatchRequest domain =
                RuntimeTraceReplayBatchRequest.byConversation(
                        principal.userId(),
                        conversationId,
                        Optional.ofNullable(dto.createdAtFrom()),
                        Optional.ofNullable(dto.createdAtTo()),
                        workflowName);
        try {
            return respond(RuntimeTraceReplayBatchMode.BY_CONVERSATION, batchService.execute(domain));
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * §6.1: null → empty; else trim; empty after trim → empty; else trimmed.
     */
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

    private static ResponseEntity<RuntimeTraceReplayBatchResponseDto> respond(
            RuntimeTraceReplayBatchMode mode, RuntimeTraceReplayBatchResult result) {
        if (result.batchOutcome() == RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(RuntimeTraceReplayBatchResponseDto.fromBatchResult(mode, result));
    }
}
