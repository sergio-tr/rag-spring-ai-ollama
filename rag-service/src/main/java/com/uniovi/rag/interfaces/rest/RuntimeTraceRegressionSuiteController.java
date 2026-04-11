package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteByConversationEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationBatchSpecDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteExecuteRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteEntryRequestDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P31: two POST routes delegating only to {@link RuntimeTraceRegressionSuiteService#run}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteController {

    /** Same cap as P24 {@code RuntimeTraceReplayComparisonBatchService#MAX_RAW_TRACE_IDS} (BY_TRACE_IDS raw list). */
    private static final int MAX_RAW_TRACE_IDS_PER_ENTRY = 50;

    private static final String KIND_BY_TRACE_IDS = "BY_TRACE_IDS";
    private static final String KIND_BY_CONVERSATION = "BY_CONVERSATION";

    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper strictRegressionSuiteMapper;

    public RuntimeTraceRegressionSuiteController(
            RuntimeTraceRegressionSuiteService suiteService,
            @Qualifier(RegressionSuiteRestJacksonConfiguration.REGRESSION_SUITE_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictRegressionSuiteMapper) {
        this.suiteService = suiteService;
        this.strictRegressionSuiteMapper = strictRegressionSuiteMapper;
    }

    @PostMapping(value = "/runtime-traces/regression-suite", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeExplicitSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        final RuntimeTraceRegressionSuiteExecuteRequestDto dto;
        try {
            dto = strictRegressionSuiteMapper.readValue(body, RuntimeTraceRegressionSuiteExecuteRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.entries() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.entries().size() > RuntimeTraceRegressionSuiteService.MAX_SUITE_ENTRIES) {
            return ResponseEntity.badRequest().build();
        }
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : dto.entries()) {
            if (e == null) {
                return ResponseEntity.badRequest().build();
            }
        }
        if (!validateExplicitPolymorphicEntries(dto.entries())) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<RuntimeTraceRegressionSuiteEntry> domainEntries = mapExplicitEntries(dto.entries());
        RuntimeTraceRegressionSuiteRequest domainReq = new RuntimeTraceRegressionSuiteRequest(userId, domainEntries);
        return respond(suiteService.run(domainReq));
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/regression-suite",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeConversationScopedSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        final UUID pathConversationId;
        try {
            pathConversationId = UUID.fromString(conversationIdRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        final RuntimeTraceRegressionSuiteConversationExecuteRequestDto dto;
        try {
            dto = strictRegressionSuiteMapper.readValue(body, RuntimeTraceRegressionSuiteConversationExecuteRequestDto.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.entries() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (dto.entries().size() > RuntimeTraceRegressionSuiteService.MAX_SUITE_ENTRIES) {
            return ResponseEntity.badRequest().build();
        }
        for (RuntimeTraceRegressionSuiteConversationBatchSpecDto row : dto.entries()) {
            if (row == null) {
                return ResponseEntity.badRequest().build();
            }
        }
        UUID userId = principal.userId();
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<RuntimeTraceRegressionSuiteEntry> domainEntries = mapConversationSpecs(pathConversationId, dto.entries());
        RuntimeTraceRegressionSuiteRequest domainReq = new RuntimeTraceRegressionSuiteRequest(userId, domainEntries);
        return respond(suiteService.run(domainReq));
    }

    private static ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> respond(RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteResponseDto.fromResult(result));
    }

    private static boolean validateExplicitPolymorphicEntries(List<RuntimeTraceRegressionSuiteEntryRequestDto> entries) {
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : entries) {
            switch (e) {
                case RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto t -> {
                    if (t.kind() == null || !KIND_BY_TRACE_IDS.equals(t.kind())) {
                        return false;
                    }
                    if (t.traceIds() == null) {
                        return false;
                    }
                    if (t.traceIds().size() > MAX_RAW_TRACE_IDS_PER_ENTRY) {
                        return false;
                    }
                    for (UUID id : t.traceIds()) {
                        if (id == null) {
                            return false;
                        }
                    }
                }
                case RuntimeTraceRegressionSuiteByConversationEntryRequestDto c -> {
                    if (c.kind() == null || !KIND_BY_CONVERSATION.equals(c.kind())) {
                        return false;
                    }
                    if (c.conversationId() == null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static List<RuntimeTraceRegressionSuiteEntry> mapExplicitEntries(
            List<RuntimeTraceRegressionSuiteEntryRequestDto> entries) {
        List<RuntimeTraceRegressionSuiteEntry> out = new ArrayList<>(entries.size());
        for (RuntimeTraceRegressionSuiteEntryRequestDto e : entries) {
            switch (e) {
                case RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto t ->
                        out.add(new RuntimeTraceRegressionSuiteEntry.ByTraceIds(t.traceIds()));
                case RuntimeTraceRegressionSuiteByConversationEntryRequestDto c ->
                        out.add(
                                new RuntimeTraceRegressionSuiteEntry.ByConversation(
                                        c.conversationId(),
                                        Optional.ofNullable(c.createdAtFrom()),
                                        Optional.ofNullable(c.createdAtTo()),
                                        normalizeWorkflowName(c.workflowName())));
            }
        }
        return out;
    }

    private static List<RuntimeTraceRegressionSuiteEntry> mapConversationSpecs(
            UUID pathConversationId, List<RuntimeTraceRegressionSuiteConversationBatchSpecDto> specs) {
        List<RuntimeTraceRegressionSuiteEntry> out = new ArrayList<>(specs.size());
        for (RuntimeTraceRegressionSuiteConversationBatchSpecDto s : specs) {
            out.add(
                    new RuntimeTraceRegressionSuiteEntry.ByConversation(
                            pathConversationId,
                            Optional.ofNullable(s.createdAtFrom()),
                            Optional.ofNullable(s.createdAtTo()),
                            normalizeWorkflowName(s.workflowName())));
        }
        return out;
    }

    /**
     * §6.2: trim workflow; null or blank after trim → empty optional (same as P25/P28 batch paths).
     */
    private static Optional<String> normalizeWorkflowName(String workflowName) {
        if (workflowName == null) {
            return Optional.empty();
        }
        String trimmed = workflowName.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }
}
