package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.application.port.ClassifierLabPort;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Research Lab: async-first long operations (202 + job polling/SSE); {@code sync=true} keeps legacy inline JSON.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabController {

    private final EvaluationService evaluationService;
    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;
    private final ClassifierLabPort classifierLabClient;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;

    public LabController(
            EvaluationService evaluationService,
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties,
            ClassifierLabPort classifierLabClient,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties) {
        this.evaluationService = evaluationService;
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
        this.classifierLabClient = classifierLabClient;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        int qaSize = 0;
        try {
            qaSize = evaluationService.getQuestionsAndAnswers().size();
        } catch (Exception ignored) {
            qaSize = 0;
        }
        // Lab gates on bundled benchmark catalog availability — NOT AbstractEvaluationService#dataLoaded, which only flips true after loadData* runs (typically when an evaluation starts).
        boolean datasetsReady = qaSize > 0;
        m.put("datasets", Map.of("enabled", datasetsReady, "questionCount", qaSize));
        m.put(
                "evaluations",
                Map.of(
                        "llm", true,
                        "rag", true,
                        "classifierProxy", classifierLabClient.isConfigured(),
                        "asyncJobs", true));
        m.put(
                "classifier",
                Map.of(
                        "configured", classifierLabClient.isConfigured(),
                        "train", classifierLabClient.isConfigured(),
                        "evaluate", classifierLabClient.isConfigured()));
        m.put(
                "message",
                "Lab API — default: async (HTTP 202 + GET "
                        + apiPathProperties.getProductBasePath()
                        + "/lab/jobs/{id} or SSE .../events). Use ?sync=true for inline JSON.");
        return m;
    }

    @PostMapping("/evaluations/llm")
    public ResponseEntity<Object> evaluateLlm(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        if (sync) {
            RagFeatureConfiguration cfg = copyFeatureFlags(featureConfiguration);
            cfg.setUseRetrieval(false);
            return ResponseEntity.ok(
                    evaluationService.evaluateWithConfiguration(cfg, implementationProperties));
        }
        UUID jobId = asyncTaskService.submitEvalLlm(requireUserId(principal), projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(jobId));
    }

    @PostMapping("/evaluations/rag")
    public ResponseEntity<Object> evaluateRag(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        if (sync) {
            return ResponseEntity.ok(evaluationService.evaluate());
        }
        UUID jobId = asyncTaskService.submitEvalRag(requireUserId(principal), projectId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(jobId));
    }

    @PostMapping(value = "/classifier/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> classifierTrain(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "projectId", required = false) UUID projectId,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestPart("file") MultipartFile file,
            @RequestParam("model_name") String modelName,
            @RequestParam(value = "labels", required = false) String labels,
            @RequestPart(value = "labels_file", required = false) MultipartFile labelsFile,
            @RequestParam(value = "epochs", defaultValue = "50") int epochs,
            @RequestParam(value = "batch_size", defaultValue = "8") int batchSize)
            throws IOException {
        if (sync) {
            return ResponseEntity.ok(
                    classifierLabClient.train(file, modelName, labels, labelsFile, epochs, batchSize));
        }
        UUID jobId = asyncTaskService.submitClassifierTrain(
                requireUserId(principal), projectId, file, modelName, labels, labelsFile, epochs, batchSize);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(jobId));
    }

    @PostMapping(value = "/classifier/evaluate")
    public ResponseEntity<Object> classifierEvaluate(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "projectId", required = false) UUID projectId,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestParam(value = "modelId", required = false) String modelId,
            @RequestParam(value = "includeImages", defaultValue = "true") boolean includeImages,
            @RequestPart(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (sync) {
            return ResponseEntity.ok(classifierLabClient.evaluate(modelId, includeImages, file));
        }
        UUID jobId =
                asyncTaskService.submitClassifierEval(requireUserId(principal), projectId, modelId, includeImages, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(jobId));
    }

    @PostMapping(value = "/classifier/classify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> classifierClassifyJson(
            @RequestBody ClassifierClassifyRequest body) {
        return classifierLabClient.classify(
                body != null ? body.query() : "", body != null ? body.modelId() : null);
    }

    public record ClassifierClassifyRequest(String query, String modelId) {}

    private LabJobAcceptedDto accepted(UUID jobId) {
        String base = apiPathProperties.getProductBasePath() + "/lab/jobs/" + jobId;
        return new LabJobAcceptedDto(jobId, "ACCEPTED", base, base + "/events");
    }

    private static UUID requireUserId(RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }

    private static RagFeatureConfiguration copyFeatureFlags(RagFeatureConfiguration src) {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        c.setExpansionEnabled(src.isExpansionEnabled());
        c.setNerEnabled(src.isNerEnabled());
        c.setToolsEnabled(src.isToolsEnabled());
        c.setMetadataEnabled(src.isMetadataEnabled());
        c.setReasoningEnabled(src.isReasoningEnabled());
        c.setRankerEnabled(src.isRankerEnabled());
        c.setPostRetrievalEnabled(src.isPostRetrievalEnabled());
        c.setFunctionCallingEnabled(src.isFunctionCallingEnabled());
        c.setUseRetrieval(src.isUseRetrieval());
        c.setUseAdvisor(src.isUseAdvisor());
        return c;
    }
}
