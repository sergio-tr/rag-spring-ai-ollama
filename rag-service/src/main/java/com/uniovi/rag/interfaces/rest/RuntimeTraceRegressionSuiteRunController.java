package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.application.service.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunPersistenceService;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSnapshot;
import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSourceType;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunListResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * P42: read-only HTTP list/detail for persisted regression suite runs via {@link RuntimeTraceRegressionSuiteRunPersistenceService} only.
 *
 * <p>P46: two {@code POST} routes execute an ad hoc suite via {@link RuntimeTraceRegressionSuiteService#execute} then persist the
 * result with {@link RuntimeTraceRegressionSuiteRunPersistenceService#createRun} when the outcome allows - no bridge {@code @Service}.
 *
 * <p>P49: {@code DELETE …/runtime-trace-regression-suite-runs/{runId}} calls {@link
 * RuntimeTraceRegressionSuiteRunPersistenceService#deleteRunForUser} only - no bridge {@code @Service}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteRunController {

    private final RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService;
    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper strictRegressionSuiteMapper;
    private final String productBasePath;

    public RuntimeTraceRegressionSuiteRunController(
            RuntimeTraceRegressionSuiteRunPersistenceService runPersistenceService,
            RuntimeTraceRegressionSuiteService suiteService,
            @Qualifier(RegressionSuiteRestJacksonConfiguration.REGRESSION_SUITE_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictRegressionSuiteMapper,
            @Value("${rag.api.product-base-path}") String productBasePath) {
        this.runPersistenceService = runPersistenceService;
        this.suiteService = suiteService;
        this.strictRegressionSuiteMapper = strictRegressionSuiteMapper;
        this.productBasePath = productBasePath;
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

    @PostMapping(value = "/runtime-trace-regression-suite-runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createRunFromExplicitSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedExplicit> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseExplicit(body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(parsed.get().domainRequest());
        return executeAndPersistAdHoc(userId, result);
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-trace-regression-suite-runs",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createRunFromConversationScopedSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedConversationScoped> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseConversationScoped(
                        conversationIdRaw, body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        RuntimeTraceRegressionSuiteResult result = suiteService.execute(parsed.get().domainRequest());
        return executeAndPersistAdHoc(userId, result);
    }

    @DeleteMapping("/runtime-trace-regression-suite-runs/{runId}")
    public ResponseEntity<Void> deleteRun(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("runId") String runIdRaw,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<UUID> runIdOpt = parseUuid(runIdRaw);
        if (runIdOpt.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        boolean deleted = runPersistenceService.deleteRunForUser(runIdOpt.get(), principal.userId());
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<Void> executeAndPersistAdHoc(UUID userId, RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        UUID createdId =
                runPersistenceService.createRun(
                        userId, RuntimeTraceRegressionSuiteRunSourceType.AD_HOC, Optional.empty(), result);
        String location = productBasePath + "/runtime-trace-regression-suite-runs/" + createdId;
        return ResponseEntity.created(URI.create(location)).build();
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
