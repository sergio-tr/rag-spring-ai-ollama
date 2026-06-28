package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.KnowledgeVectorMetadataBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeVectorMetadataBackfillService knowledgeVectorMetadataBackfillService;

    public AdminKnowledgeController(KnowledgeVectorMetadataBackfillService knowledgeVectorMetadataBackfillService) {
        this.knowledgeVectorMetadataBackfillService = knowledgeVectorMetadataBackfillService;
    }

    @PostMapping("/backfill-vector-metadata")
    public ResponseEntity<Map<String, Object>> backfillVectorMetadata(@RequestParam("projectId") UUID projectId) {
        int updated = knowledgeVectorMetadataBackfillService.backfillProject(projectId);
        return ResponseEntity.ok(Map.of("updatedRows", updated));
    }
}
