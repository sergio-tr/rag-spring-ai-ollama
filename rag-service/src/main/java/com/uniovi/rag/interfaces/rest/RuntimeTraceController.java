package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceController {

    private final RuntimeTraceQueryService runtimeTraceQueryService;

    public RuntimeTraceController(RuntimeTraceQueryService runtimeTraceQueryService) {
        this.runtimeTraceQueryService = runtimeTraceQueryService;
    }

    @GetMapping("/conversations/{conversationId}/runtime-traces")
    public Page<RuntimeExecutionTraceSummaryDto> listConversationTraces(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(name = "createdAtFrom", required = false) Instant createdAtFrom,
            @RequestParam(name = "createdAtTo", required = false) Instant createdAtTo,
            @RequestParam(name = "workflowName", required = false) String workflowName,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size
    ) {
        return runtimeTraceQueryService.listConversationTraceSummaries(
                principal.userId(),
                conversationId,
                Optional.ofNullable(createdAtFrom),
                Optional.ofNullable(createdAtTo),
                Optional.ofNullable(workflowName),
                page,
                size);
    }

    @GetMapping("/runtime-traces/{traceId}")
    public RuntimeExecutionTraceDetailDto getTraceById(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID traceId
    ) {
        return runtimeTraceQueryService.getTraceDetailById(principal.userId(), traceId);
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/runtime-trace")
    public RuntimeExecutionTraceDetailDto getTraceByMessage(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId
    ) {
        return runtimeTraceQueryService.getMostRecentTraceDetailByMessageId(
                principal.userId(), conversationId, messageId);
    }
}

