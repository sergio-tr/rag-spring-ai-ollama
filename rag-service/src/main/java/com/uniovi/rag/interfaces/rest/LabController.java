package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.LabJobAcceptedDto;
import com.uniovi.rag.interfaces.rest.dto.ExperimentalPresetCatalogItemDto;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleCounts;
import com.uniovi.rag.application.evaluation.workbook.ReferenceBundleSnapshot;
import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.domain.evaluation.workbook.ValidationIssue;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.application.port.ClassifierLabPort;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Research Lab: async-first long operations (202 + job polling/SSE); canonical benchmarks use
 * {@code POST …/lab/benchmarks/{kind}/runs}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabController {

    private final ClassifierLabPort classifierLabClient;
    private final AsyncTaskService asyncTaskService;
    private final RagApiPathProperties apiPathProperties;
    private final EvaluationReferenceBundleLoader referenceBundleLoader;
    private final LabExperimentalPresetCatalogService experimentalPresetCatalogService;

    public LabController(
            ClassifierLabPort classifierLabClient,
            AsyncTaskService asyncTaskService,
            RagApiPathProperties apiPathProperties,
            EvaluationReferenceBundleLoader referenceBundleLoader,
            LabExperimentalPresetCatalogService experimentalPresetCatalogService) {
        this.classifierLabClient = classifierLabClient;
        this.asyncTaskService = asyncTaskService;
        this.apiPathProperties = apiPathProperties;
        this.referenceBundleLoader = referenceBundleLoader;
        this.experimentalPresetCatalogService = experimentalPresetCatalogService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        ReferenceBundleSnapshot bundleSnap = referenceBundleLoader.getSnapshot();
        boolean bundleAvailable = bundleSnap.classpathResourcePresent();
        boolean bundleValid = bundleSnap.validForReferenceUse();
        boolean kindsReady = datasetKindsReady(bundleSnap);

        m.put("referenceBundleAvailable", bundleAvailable);
        m.put("referenceBundleValid", bundleValid);
        m.put("datasetKindsReady", kindsReady);

        Optional<String> pv = bundleSnap.protocolVersion();
        if (pv.isPresent()) {
            m.put("protocolVersion", pv.get());
        }
        m.put("countsByDatasetKind", bundleSnap.countsByDatasetKind());

        List<Map<String, Object>> issueMaps = validationIssuesPayload(bundleSnap);
        if (!issueMaps.isEmpty()) {
            m.put("validationIssues", issueMaps);
        }

        Map<String, Object> datasets = new LinkedHashMap<>();
        datasets.put("enabled", kindsReady);
        datasets.put("datasetKindsReady", kindsReady);
        datasets.put("legacyQuestionCountDeprecated", null);
        m.put("datasets", datasets);

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
                "Lab API — canonical benchmarks: POST "
                        + apiPathProperties.getProductBasePath()
                        + "/lab/benchmarks/{kind}/runs with a typed evaluation_dataset. Async jobs: GET "
                        + apiPathProperties.getProductBasePath()
                        + "/lab/jobs/{id} or SSE …/events.");
        return m;
    }

    @PostMapping("/evaluations/llm")
    public ResponseEntity<Map<String, Object>> evaluateLlm(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        requireUserId(principal);
        return legacyEvaluationsRemoved();
    }

    @PostMapping("/evaluations/rag")
    public ResponseEntity<Map<String, Object>> evaluateRag(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(name = "sync", defaultValue = "false") boolean sync,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        requireUserId(principal);
        return legacyEvaluationsRemoved();
    }

    @GetMapping("/experimental-presets")
    public List<ExperimentalPresetCatalogItemDto> experimentalPresets() {
        return experimentalPresetCatalogService.list();
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

    private ResponseEntity<Map<String, Object>> legacyEvaluationsRemoved() {
        String base = apiPathProperties.getProductBasePath();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "LAB_EVALUATIONS_LEGACY_REMOVED");
        body.put(
                "message",
                "POST /lab/evaluations/llm and /lab/evaluations/rag are removed. "
                        + "Use POST /lab/benchmarks/{kind}/runs with a typed evaluation_dataset id.");
        body.put("canonicalStartBenchmarkPathTemplate", base + "/lab/benchmarks/{kind}/runs");
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

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

    private static boolean datasetKindsReady(ReferenceBundleSnapshot snap) {
        if (!snap.validForReferenceUse()) {
            return false;
        }
        ReferenceBundleCounts c = snap.counts();
        return c.llmReaderQuestions() > 0 && c.embeddingRetrievalQueries() > 0 && c.ragPresetQuestions() > 0;
    }

    private static List<Map<String, Object>> validationIssuesPayload(ReferenceBundleSnapshot snap) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ValidationIssue i : snap.validationReport().issues()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("severity", i.severity().name());
            row.put("code", i.code().name());
            row.put("sheet", i.sheet());
            row.put("rowNumber", i.rowNumber());
            row.put("column", i.column());
            row.put("message", i.message());
            out.add(row);
        }
        return out;
    }
}
