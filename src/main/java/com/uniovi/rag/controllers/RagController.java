package com.uniovi.rag.controllers;

import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v3")
public class RagController {

    private final DocumentService documentService;
    private final QueryService queryService;
    private final EvaluationService evaluationService;

    public RagController(DocumentService documentService, QueryService queryService, EvaluationService evaluationService) {
        this.documentService = documentService;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Error: File is null or empty");
            }

            String filename = file.getOriginalFilename();
            if (filename == null || filename.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Error: Filename is null or empty");
            }

            documentService.processDocument(file);
            return ResponseEntity.ok("Document stored successfully: " + filename);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Error processing document " +
                    (file != null ? file.getOriginalFilename() : "unknown") + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error storing document " +
                    (file != null ? file.getOriginalFilename() : "unknown") + ": " + e.getMessage());
            documentService.log().error(e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error storing document " +
                    (file != null ? file.getOriginalFilename() : "unknown") + ": " + e.getMessage());
        }
    }

    @GetMapping("/query")
    public ResponseEntity<String> query(@RequestParam String question) {
        String response = queryService.generateResponse(question);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        evaluationService.loadData();
        Map<String, Object> results = evaluationService.evaluate();
        return ResponseEntity.ok(results);
    }
}
