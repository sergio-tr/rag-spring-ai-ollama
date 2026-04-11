package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionService;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntrySnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionUserSummary;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionEntryDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionListResponseDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionSummaryDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P34: read-only GET routes for persisted regression suite definitions — delegates only to
 * {@link RuntimeTraceRegressionSuiteDefinitionService}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteDefinitionController {

    private final RuntimeTraceRegressionSuiteDefinitionService definitionService;

    public RuntimeTraceRegressionSuiteDefinitionController(RuntimeTraceRegressionSuiteDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @GetMapping("/runtime-trace-regression-suite-definitions")
    public ResponseEntity<RuntimeTraceRegressionSuiteDefinitionListResponseDto> listDefinitions(
            @AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(toListResponse(definitionService.listSummariesForUser(principal.userId())));
    }

    @GetMapping("/runtime-trace-regression-suite-definitions/{definitionId}")
    public ResponseEntity<RuntimeTraceRegressionSuiteDefinitionDetailDto> getDefinition(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("definitionId") String definitionIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> definitionId = parseDefinitionId(definitionIdRaw);
        if (definitionId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<RuntimeTraceRegressionSuiteDefinitionSnapshot> snap =
                definitionService.loadByIdForUser(definitionId.get(), principal.userId());
        if (snap.isEmpty()) {
            throw new NotFoundException("definition not found");
        }
        return ResponseEntity.ok(toDetailDto(snap.get()));
    }

    private static Optional<UUID> parseDefinitionId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static RuntimeTraceRegressionSuiteDefinitionListResponseDto toListResponse(
            List<RuntimeTraceRegressionSuiteDefinitionUserSummary> rows) {
        List<RuntimeTraceRegressionSuiteDefinitionSummaryDto> defs = new ArrayList<>(rows.size());
        for (RuntimeTraceRegressionSuiteDefinitionUserSummary s : rows) {
            defs.add(
                    new RuntimeTraceRegressionSuiteDefinitionSummaryDto(
                            s.definitionId(), s.name(), s.description(), s.entryCount(), s.createdAt(), s.updatedAt()));
        }
        return new RuntimeTraceRegressionSuiteDefinitionListResponseDto(defs);
    }

    private static RuntimeTraceRegressionSuiteDefinitionDetailDto toDetailDto(RuntimeTraceRegressionSuiteDefinitionSnapshot snap) {
        List<RuntimeTraceRegressionSuiteDefinitionEntryDto> entryDtos = new ArrayList<>(snap.entries().size());
        for (RuntimeTraceRegressionSuiteDefinitionEntrySnapshot e : snap.entries()) {
            entryDtos.add(toEntryDto(e));
        }
        return new RuntimeTraceRegressionSuiteDefinitionDetailDto(
                snap.id(),
                snap.name(),
                snap.description(),
                snap.schemaVersion(),
                snap.createdAt(),
                snap.updatedAt(),
                entryDtos);
    }

    private static RuntimeTraceRegressionSuiteDefinitionEntryDto toEntryDto(RuntimeTraceRegressionSuiteDefinitionEntrySnapshot snap) {
        return switch (snap) {
            case RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByTraceIds b ->
                    new RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto(b.traceIds());
            case RuntimeTraceRegressionSuiteDefinitionEntrySnapshot.ByConversation c ->
                    new RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto(
                            c.conversationId(), c.createdAtFrom(), c.createdAtTo(), c.workflowName());
        };
    }
}
