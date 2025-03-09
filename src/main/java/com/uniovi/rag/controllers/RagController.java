package com.uniovi.rag.controllers;

import com.uniovi.rag.services.DocumentService;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.query.SimpleQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RagController {

    private final DocumentService documentService;
    private final SimpleQueryService queryService;
    private final EvaluationService evaluationService;

    public RagController(DocumentService documentService, SimpleQueryService queryService, EvaluationService evaluationService) {
        this.documentService = documentService;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        documentService.processAndStoreDocument(file);
        return ResponseEntity.ok("Documento procesado correctamente");
    }

    @GetMapping("/query")
    public ResponseEntity<String> query(@RequestParam String question) {
        String response = queryService.generateResponse(question);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        // evaluationService.loadData();
        Map<String, Object> results = evaluationService.evaluate();
        return ResponseEntity.ok(results);
    }
}
