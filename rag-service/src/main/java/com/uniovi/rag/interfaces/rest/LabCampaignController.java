package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.LabCampaignService;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.application.service.evaluation.BenchmarkJobAccepted;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.interfaces.rest.dto.StartCampaignRequestDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabCampaignController {

    private final LabCampaignService labCampaignService;
    private final BenchmarkRunOrchestrator benchmarkRunOrchestrator;

    public LabCampaignController(LabCampaignService labCampaignService, BenchmarkRunOrchestrator benchmarkRunOrchestrator) {
        this.labCampaignService = labCampaignService;
        this.benchmarkRunOrchestrator = benchmarkRunOrchestrator;
    }

    @PostMapping(value = "/campaigns", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> startCampaign(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody StartCampaignRequestDto body) {
        UUID userId = requireUserId(principal);
        BenchmarkKind kind = mapKind(body != null ? body.campaignKind() : null);
        return labCampaignService.startCampaign(userId, kind, body, benchmarkRunOrchestrator);
    }

    @GetMapping("/campaigns/{campaignId}")
    public Map<String, Object> summary(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return labCampaignService.summary(requireUserId(principal), campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/runs")
    public List<Map<String, Object>> listRuns(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return labCampaignService.listRuns(requireUserId(principal), campaignId);
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/mvp/items.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportMvpItemsJson(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return ResponseEntity.ok(labCampaignService.exportCampaignMvpItemsJson(requireUserId(principal), campaignId));
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/campaign-items.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportCampaignItemsJson(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return ResponseEntity.ok(labCampaignService.exportCampaignItemsJson(requireUserId(principal), campaignId));
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/campaign-summary.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportCampaignSummaryJson(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return ResponseEntity.ok(labCampaignService.exportCampaignSummaryJson(requireUserId(principal), campaignId));
    }

    @GetMapping(value = "/campaigns/{campaignId}/comparison", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> comparison(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return labCampaignService.campaignComparison(requireUserId(principal), campaignId);
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/items.csv", produces = "text/csv")
    public ResponseEntity<String> exportItemsCsv(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return exportCampaignItemsCsvResponse(requireUserId(principal), campaignId);
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/mvp/items.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportMvpItemsCsv(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return exportCampaignItemsCsvResponse(requireUserId(principal), campaignId);
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/summary.csv", produces = "text/csv")
    public ResponseEntity<String> exportSummaryCsv(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        String csv = labCampaignService.exportCampaignSummaryCsv(requireUserId(principal), campaignId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-" + campaignId + "-summary.csv\"")
                .body(csv);
    }

    @GetMapping(value = "/campaigns/{campaignId}/export/bundle.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportBundle(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID campaignId) {
        return ResponseEntity.ok(labCampaignService.exportCampaignBundleJson(requireUserId(principal), campaignId));
    }

    private static BenchmarkKind mapKind(String raw) {
        String k = raw != null ? raw.trim() : "";
        if (k.equalsIgnoreCase("LLM_MODEL_SWEEP") || k.equalsIgnoreCase("LLM_MODEL_BASELINE") || k.equalsIgnoreCase("LLM_MODEL_SWEEP_CAMPAIGN")) {
            return BenchmarkKind.LLM_JUDGE_QA;
        }
        if (k.equalsIgnoreCase("EMBEDDING_MODEL_SWEEP") || k.equalsIgnoreCase("EMBEDDING_MODEL_BASELINE")) {
            return BenchmarkKind.EMBEDDING_RETRIEVAL;
        }
        if (k.equalsIgnoreCase("RAG_PRESET_SWEEP") || k.equalsIgnoreCase("RAG_PRESET_BENCHMARK")) {
            return BenchmarkKind.RAG_PRESET_END_TO_END;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown campaignKind: " + k);
    }

    private ResponseEntity<String> exportCampaignItemsCsvResponse(UUID userId, UUID campaignId) {
        String csv = labCampaignService.exportCampaignItemsCsv(userId, campaignId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"campaign-" + campaignId + "-items.csv\"")
                .body(csv);
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}

