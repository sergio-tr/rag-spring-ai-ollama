package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunListResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/** P42: read-only HTTP list/detail for persisted regression suite runs via {@link RuntimeTraceRegressionSuiteRunPersistenceService} only. */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteRunController {

    private final RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;

    public RuntimeTraceRegressionSuiteRunController(RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService) {
        this.runPersistenceService = runPersistenceService;
    }

    @GetMapping("/runtime-trace-regression-suite-runs")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunListResponseDto> listRuns(
            @AuthenticationPrincipal RagPrincipal principal, HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                RuntimeTraceRegressionSuiteRunListResponseDto.fromSummaries(
                        runPersistenceService.listSummariesForUser(principal.userId())));
    }

    @GetMapping("/runtime-trace-regression-suite-runs/{runId}")
    public ResponseEntity<RuntimeTraceRegressionSuiteRunDetailDto> getRun(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runId = parseUuid(runIdRaw);
        if (runId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<RuntimeTraceRegressionSuiteRunSnapshot> snap =
                runPersistenceService.loadByIdForUser(runId.get(), principal.userId());
        if (snap.isEmpty()) {
            throw new NotFoundException("run not found");
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteRunDetailDto.fromSnapshot(snap.get()));
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
