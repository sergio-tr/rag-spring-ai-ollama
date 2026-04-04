package com.uniovi.rag.interfaces.rest.legacy;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.exception.DocumentAlreadyExistsException;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.query.QueryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.uniovi.rag.application.evaluation.EvaluationCustomConfigMapper;
import com.uniovi.rag.interfaces.rest.support.dto.ApiResponse;
import com.uniovi.rag.interfaces.rest.support.dto.QuerySuccessPayload;
import com.uniovi.rag.infrastructure.observability.Loggable;
import java.util.Map;
import com.uniovi.rag.application.model.QueryResponse;

@RestController
@RequestMapping("${rag.api.legacy-base-path}")
@ConditionalOnProperty(prefix = "rag.api", name = "legacy-controllers-enabled", havingValue = "true", matchIfMissing = true)
public class RagController implements Loggable {

    private static final String UNKNOWN_FILENAME_PLACEHOLDER = "unknown";

    private final DocumentService documentService;
    private final QueryService queryService;
    private final EvaluationService evaluationService;
    private final MinuteDocumentRepository minuteDocumentRepository;
    private final EvaluationCustomConfigMapper evaluationCustomConfigMapper;
    private final ObservabilitySupport observability;

    public RagController(DocumentService documentService, QueryService queryService, EvaluationService evaluationService,
                         MinuteDocumentRepository minuteDocumentRepository,
                         EvaluationCustomConfigMapper evaluationCustomConfigMapper,
                         @Autowired(required = false) ObservabilitySupport observability) {
        this.documentService = documentService;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
        this.minuteDocumentRepository = minuteDocumentRepository;
        this.evaluationCustomConfigMapper = evaluationCustomConfigMapper;
        this.observability = observability;
    }

    /**
     * Adds a minute to the knowledge base by JSON body.
     * Returns 409 Conflict if a document with the same id already exists.
     */
    @PostMapping("/documents/minute")
    public ResponseEntity<String> addMinute(@RequestBody Minute minute) {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.addMinute",
                    Map.of("minuteId", minute != null && minute.id() != null ? minute.id() : ""),
                    "result",
                    () -> addMinuteImpl(minute));
        }
        return addMinuteImpl(minute);
    }

    private ResponseEntity<String> addMinuteImpl(Minute minute) {
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
        if (observability != null) {
            return observability.runWithSpan("rag.controller.deleteDocumentById",
                    Map.of("documentId", id != null ? id : ""),
                    "result",
                    () -> deleteDocumentByIdImpl(id));
        }
        return deleteDocumentByIdImpl(id);
    }

    private ResponseEntity<String> deleteDocumentByIdImpl(String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body("Document id must be non-blank");
        }
        minuteDocumentRepository.deleteById(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.uploadDocument",
                    Map.of("filename", file != null && file.getOriginalFilename() != null ? file.getOriginalFilename() : ""),
                    "result",
                    () -> uploadDocumentImpl(file));
        }
        return uploadDocumentImpl(file);
    }

    private ResponseEntity<String> uploadDocumentImpl(MultipartFile file) {
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
                    (file != null ? file.getOriginalFilename() : UNKNOWN_FILENAME_PLACEHOLDER) + ": " + e.getMessage());
        } catch (Exception e) {
            if (e instanceof DocumentAlreadyExistsException docEx) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Document already in knowledge base: " + docEx.getDocumentId());
            }
            // Mapped to HTTP 400 with body; log message only (details are in response body; avoids huge stacks in CI/tests).
            String fn = file != null ? file.getOriginalFilename() : UNKNOWN_FILENAME_PLACEHOLDER;
            log().warn("Error storing document {}: {}", fn, e.getMessage());
            return ResponseEntity.badRequest().body("Error storing document " +
                    (file != null ? file.getOriginalFilename() : UNKNOWN_FILENAME_PLACEHOLDER) + ": " + e.getMessage());
        }
    }

    /**
     * RAG query. Returns JSON envelope {@link ApiResponse} with {@link QuerySuccessPayload} on success,
     * or {@code success: false} + error code (e.g. {@code LLM_UNAVAILABLE}, HTTP 503) when Ollama is unreachable.
     *
     * @param chatModel optional Ollama chat model for this request (lab); if omitted, the default from configuration is used.
     *                  Before answering, the backend checks connectivity and pulls missing embedding/chat models when {@code rag.ollama.auto-pull-enabled=true}.
     */
    @GetMapping(value = "/query", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<QuerySuccessPayload>> query(
            @RequestParam String question,
            @RequestParam(required = false) String chatModel) {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.query",
                    Map.of("question", question != null ? question : ""),
                    "result",
                    () -> {
                        QueryResponse response = queryService.generateResponse(question, chatModel);
                        return ResponseEntity.ok(ApiResponse.ok(QuerySuccessPayload.from(response)));
                    });
        }
        QueryResponse response = queryService.generateResponse(question, chatModel);
        return ResponseEntity.ok(ApiResponse.ok(QuerySuccessPayload.from(response)));
    }

    @GetMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate() {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.evaluate", Map.of(), "result", () -> {
                evaluationService.loadData();
                Map<String, Object> results = evaluationService.evaluate();
                return ResponseEntity.ok(results);
            });
        }
        evaluationService.loadData();
        Map<String, Object> results = evaluationService.evaluate();
        return ResponseEntity.ok(results);
    }
    
    /**
     * Evaluates with a specific custom configuration.
     * POST body may contain: expansion, ner, tools, metadata, reasoning, ranker, post-retrieval, function-calling, use-retrieval, use-advisor (boolean);
     * and query-service-impl, retriever-impl, analyser-impl (string, optional).
     */
    @PostMapping("/evaluate/custom")
    public ResponseEntity<Map<String, Object>> evaluateWithCustomConfig(@RequestBody Map<String, Object> config) {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.evaluateWithCustomConfig", Map.of(), "result",
                    () -> evaluateWithCustomConfigImpl(config));
        }
        return evaluateWithCustomConfigImpl(config);
    }

    private ResponseEntity<Map<String, Object>> evaluateWithCustomConfigImpl(Map<String, Object> config) {
        evaluationService.loadData();
        EvaluationCustomConfigMapper.FeatureAndImplementation pair = evaluationCustomConfigMapper.fromRequestBody(config);
        Map<String, Object> results =
                evaluationService.evaluateWithConfiguration(pair.features(), pair.impl());
        results.put(
                "config",
                evaluationCustomConfigMapper.implementationsBlock(pair.features(), pair.impl()));
        return ResponseEntity.ok(results);
    }
    
    /**
     * Evaluates all possible configuration combinations of the main feature flags (2^8 = 256 as of current descriptors).
     * This may take a long time as it runs the full evaluation for each configuration.
     */
    @GetMapping("/evaluate/all")
    public ResponseEntity<Map<String, Map<String, Object>>> evaluateAllConfigurations() {
        if (observability != null) {
            return observability.runWithSpan("rag.controller.evaluateAllConfigurations", Map.of(), "result", () -> {
                evaluationService.loadData();
                Map<String, Map<String, Object>> allResults = evaluationService.evaluateAllConfigurations();
                return ResponseEntity.ok(allResults);
            });
        }
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
        if (observability != null) {
            return observability.runWithSpan("rag.controller.clearDatabase", Map.of(), "result", this::clearDatabaseImpl);
        }
        return clearDatabaseImpl();
    }

    private ResponseEntity<String> clearDatabaseImpl() {
        try {
            documentService.clearDatabase();
            return ResponseEntity.ok("Database cleared successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error clearing database: " + e.getMessage());
        }
    }
}
