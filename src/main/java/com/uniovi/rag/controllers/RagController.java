package com.uniovi.rag.controllers;

import com.uniovi.rag.model.AddResult;
import com.uniovi.rag.model.DocumentAlreadyExistsException;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.repository.MinuteDocumentRepository;
import com.uniovi.rag.services.document.DocumentService;
import com.uniovi.rag.services.evaluation.EvaluationService;
import com.uniovi.rag.services.query.QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.model.Loggable;
import java.util.Map;
import com.uniovi.rag.model.QueryResponse;

@RestController
@RequestMapping("/api/v4")
public class RagController implements Loggable {

    private final DocumentService documentService;
    private final QueryService queryService;
    private final EvaluationService evaluationService;
    private final MinuteDocumentRepository minuteDocumentRepository;

    public RagController(DocumentService documentService, QueryService queryService, EvaluationService evaluationService,
                         MinuteDocumentRepository minuteDocumentRepository) {
        this.documentService = documentService;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
        this.minuteDocumentRepository = minuteDocumentRepository;
    }

    /**
     * Adds a minute to the knowledge base by JSON body.
     * Returns 409 Conflict if a document with the same id already exists.
     */
    @PostMapping("/documents/minute")
    public ResponseEntity<String> addMinute(@RequestBody Minute minute) {
        if (minute == null || minute.id() == null || minute.id().isBlank()) {
            return ResponseEntity.badRequest().body("Minute and minute.id() must be non-null and non-blank");
        }
        AddResult result = minuteDocumentRepository.addMinute(minute);
        if (result == AddResult.ALREADY_EXISTS) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Document already in knowledge base: " + minute.id());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Document added successfully: " + minute.id());
    }

    /**
     * Deletes all chunks and document entries for the given document id.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<String> deleteDocumentById(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Document id must be non-blank");
        }
        minuteDocumentRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
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
            if (e instanceof DocumentAlreadyExistsException docEx) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Document already in knowledge base: " + docEx.getDocumentId());
            }
            log().error("Error storing document " +
                    (file != null ? file.getOriginalFilename() : "unknown") + ": " + e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error storing document " +
                    (file != null ? file.getOriginalFilename() : "unknown") + ": " + e.getMessage());
        }
    }

    @GetMapping("/query")
    public ResponseEntity<String> query(@RequestParam String question) {
        QueryResponse response = queryService.generateResponse(question);
        return ResponseEntity.ok(response.getAnswer());
    }

    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        evaluationService.loadData();
        Map<String, Object> results = evaluationService.evaluate();
        return ResponseEntity.ok(results);
    }
    
    /**
     * Evaluates with a specific custom configuration.
     * POST body may contain: expansion, ner, tools, metadata, reasoning, ranker, post-retrieval, tool-rag, function-calling (all boolean).
     * Used for the 5 scenarios in EVALUATION_RAG_IMPROVEMENTS (baseline, reasoning, reasoning+ranker, function-calling, tool-rag).
     */
    @PostMapping("/evaluate/custom")
    public ResponseEntity<Map<String, Object>> evaluateWithCustomConfig(@RequestBody Map<String, Boolean> config) {
        evaluationService.loadData();

        RagFeatureConfiguration customConfig = new RagFeatureConfiguration();
        customConfig.setExpansionEnabled(config.getOrDefault("expansion", false));
        customConfig.setNerEnabled(config.getOrDefault("ner", false));
        customConfig.setToolsEnabled(config.getOrDefault("tools", false));
        customConfig.setMetadataEnabled(config.getOrDefault("metadata", false));
        customConfig.setReasoningEnabled(config.getOrDefault("reasoning", false));
        customConfig.setRankerEnabled(config.getOrDefault("ranker", false));
        customConfig.setPostRetrievalEnabled(config.getOrDefault("post-retrieval", false));
        customConfig.setToolRagEnabled(config.getOrDefault("tool-rag", false));
        customConfig.setFunctionCallingEnabled(config.getOrDefault("function-calling", false));

        Map<String, Object> results = evaluationService.evaluateWithConfiguration(customConfig);
        return ResponseEntity.ok(results);
    }
    
    /**
     * Evaluates all possible configuration combinations (16 combinations).
     * This may take a long time as it runs the full evaluation for each configuration.
     */
    @GetMapping("/evaluate/all")
    public ResponseEntity<Map<String, Map<String, Object>>> evaluateAllConfigurations() {
        evaluationService.loadData();
        Map<String, Map<String, Object>> allResults = evaluationService.evaluateAllConfigurations();
        return ResponseEntity.ok(allResults);
    }
    
    /**
     * Clears all documents from the database.
     * Useful for testing or when switching between different configurations.
     */
    @DeleteMapping("/documents")
    public ResponseEntity<String> clearDatabase() {
        try {
            documentService.clearDatabase();
            return ResponseEntity.ok("Database cleared successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error clearing database: " + e.getMessage());
        }
    }
}
