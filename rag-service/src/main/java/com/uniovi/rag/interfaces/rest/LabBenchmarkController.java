package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.BenchmarkJobAccepted;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.application.service.evaluation.LabEvaluationRunService;
import com.uniovi.rag.application.service.evaluation.LabMetricsComparisonService;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.interfaces.rest.dto.BenchmarkJobAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationResultItemDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import com.uniovi.rag.interfaces.rest.dto.MetricsCompareRequestDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical lab benchmark runs and exports ({@code evaluation_run} + {@code async_task}).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabBenchmarkController {

    private final BenchmarkRunOrchestrator benchmarkRunOrchestrator;
    private final LabEvaluationRunService labEvaluationRunService;
    private final LabMetricsComparisonService labMetricsComparisonService;
    private final RagApiPathProperties apiPathProperties;

    public LabBenchmarkController(
            BenchmarkRunOrchestrator benchmarkRunOrchestrator,
            LabEvaluationRunService labEvaluationRunService,
            LabMetricsComparisonService labMetricsComparisonService,
            RagApiPathProperties apiPathProperties) {
        this.benchmarkRunOrchestrator = benchmarkRunOrchestrator;
        this.labEvaluationRunService = labEvaluationRunService;
        this.labMetricsComparisonService = labMetricsComparisonService;
        this.apiPathProperties = apiPathProperties;
    }

    @PostMapping(value = "/benchmarks/{kind}/runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BenchmarkJobAcceptedDto> startBenchmarkJson(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable String kind,
            @RequestBody StartBenchmarkRunRequest body) {
        BenchmarkKind bk = parseBenchmarkKind(kind);
        if (bk == BenchmarkKind.CLASSIFIER_METRICS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Classifier metrics benchmarks require a spreadsheet upload — use the classifier evaluation"
                            + " form with an Excel file instead of a JSON request.");
        }
        BenchmarkJobAccepted accepted =
                benchmarkRunOrchestrator.startJsonBenchmark(
                        requireUserId(principal), principal.roleName(), bk, body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toAcceptedDto(accepted));
    }

    @PostMapping(value = "/benchmarks/CLASSIFIER_METRICS/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BenchmarkJobAcceptedDto> startClassifierMetrics(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam("datasetId") UUID datasetId,
            @RequestParam(name = "projectId", required = false) UUID projectId,
            @RequestParam(name = "runKind", required = false) EvaluationRunKind runKind,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "resolvedConfigSnapshotId", required = false) UUID resolvedConfigSnapshotId,
            @RequestParam(name = "indexSnapshotId", required = false) UUID indexSnapshotId,
            @RequestParam(name = "presetId", required = false) UUID presetId,
            @RequestParam(name = "modelId", required = false) String modelId,
            @RequestParam(name = "includeImages", defaultValue = "true") boolean includeImages,
            @RequestPart(value = "file", required = false) MultipartFile file)
            throws IOException {
        StartBenchmarkRunRequest meta =
                new StartBenchmarkRunRequest(
                        datasetId,
                        null,
                        projectId,
                        runKind,
                        name,
                        resolvedConfigSnapshotId,
                        indexSnapshotId,
                        presetId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());
        BenchmarkJobAccepted accepted =
                benchmarkRunOrchestrator.startClassifierMetrics(
                        requireUserId(principal), principal.roleName(), meta, modelId, includeImages, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toAcceptedDto(accepted));
    }

    @GetMapping("/runs/{runId}")
    public EvaluationRunDetailDto getRun(@AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID runId) {
        return labEvaluationRunService.getRun(requireUserId(principal), runId);
    }

    @GetMapping("/runs/{runId}/items")
    public List<EvaluationResultItemDto> listItems(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID runId) {
        return labEvaluationRunService.listItems(requireUserId(principal), runId);
    }

    @GetMapping("/runs/compare")
    public CompareRunsResponseDto compare(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam("runA") UUID runA,
            @RequestParam("runB") UUID runB) {
        return labEvaluationRunService.compare(requireUserId(principal), runA, runB);
    }

    /**
     * Metrics comparison for N compatible runs (scientific leaderboard/diff).
     */
    @PostMapping(value = "/runs/compare/metrics", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> compareMetrics(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody MetricsCompareRequestDto body) {
        List<UUID> ids = body != null ? body.runIds() : null;
        return labMetricsComparisonService.compareMetrics(
                requireUserId(principal),
                ids,
                body != null ? body.queryTypes() : null,
                body != null ? body.difficulties() : null);
    }

    @PostMapping(
            value = "/runs/compare/metrics/export/comparison-summary.json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportComparisonSummaryJson(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody MetricsCompareRequestDto body) {
        return compareMetrics(principal, body);
    }

    @PostMapping(
            value = "/runs/compare/metrics/export/comparison-table.csv",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportComparisonTableCsv(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody MetricsCompareRequestDto body) {
        String csv =
                labMetricsComparisonService.exportComparisonTableCsv(
                        requireUserId(principal),
                        body != null ? body.runIds() : null,
                        body != null ? body.queryTypes() : null,
                        body != null ? body.difficulties() : null);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv);
    }

    @PostMapping(
            value = "/runs/compare/metrics/export/comparison-items.csv",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportComparisonItemsCsv(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody MetricsCompareRequestDto body) {
        String csv =
                labMetricsComparisonService.exportComparisonItemsCsv(
                        requireUserId(principal),
                        body != null ? body.runIds() : null,
                        body != null ? body.queryTypes() : null,
                        body != null ? body.difficulties() : null);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv);
    }

    @GetMapping(value = "/runs/{runId}/export")
    public ResponseEntity<Object> exportRun(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID runId,
            @RequestParam(name = "format", defaultValue = "csv") String format) {
        UUID uid = requireUserId(principal);
        if ("json".equalsIgnoreCase(format)) {
            Map<String, Object> json = labEvaluationRunService.exportJsonBundle(uid, runId);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        }
        String csv = labEvaluationRunService.exportCsv(uid, runId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(csv);
    }

    /** MVP export: nested metrics per item (JSON bundle). */
    @GetMapping(value = "/runs/{runId}/export/mvp/items.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportMvpItemsJson(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID runId) {
        return labEvaluationRunService.exportMvpItemsJsonBundle(requireUserId(principal), runId);
    }

    /** MVP export: rollups with explicit {@code outcomeCounts} (never mixes NOT_SUPPORTED into executed means). */
    @GetMapping(value = "/runs/{runId}/export/mvp/rollups.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> exportMvpRollupsJson(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID runId) {
        return labEvaluationRunService.exportMvpRollupsJson(requireUserId(principal), runId);
    }

    /** MVP export: flat CSV rows ({@code items.csv} semantics). */
    @GetMapping(value = "/runs/{runId}/export/mvp/items.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportMvpItemsCsv(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID runId) {
        String csv = labEvaluationRunService.exportMvpItemsCsv(requireUserId(principal), runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    private BenchmarkJobAcceptedDto toAcceptedDto(BenchmarkJobAccepted accepted) {
        UUID jobId = accepted.asyncTaskId();
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return new BenchmarkJobAcceptedDto(
                accepted.evaluationRunId(),
                jobId,
                accepted.campaignId().orElse(null),
                accepted.totalItems().orElse(null),
                "ACCEPTED",
                base,
                base + "/events");
    }

    private static BenchmarkKind parseBenchmarkKind(String kind) {
        try {
            return BenchmarkKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown benchmark kind: " + kind);
        }
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
