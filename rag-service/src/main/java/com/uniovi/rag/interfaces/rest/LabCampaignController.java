package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.LabCampaignService;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public LabCampaignController(LabCampaignService labCampaignService) {
        this.labCampaignService = labCampaignService;
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

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}

