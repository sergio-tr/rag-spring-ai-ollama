package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.KnowledgeLegacyBackfillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeLegacyBackfillService knowledgeLegacyBackfillService;

    public AdminKnowledgeController(KnowledgeLegacyBackfillService knowledgeLegacyBackfillService) {
        this.knowledgeLegacyBackfillService = knowledgeLegacyBackfillService;
    }

    @PostMapping("/backfill-legacy")
    public ResponseEntity<Map<String, Object>> backfillLegacy(@RequestParam("projectId") UUID projectId) {
        int updated = knowledgeLegacyBackfillService.backfillProject(projectId);
        return ResponseEntity.ok(Map.of("updatedRows", updated));
    }
}
