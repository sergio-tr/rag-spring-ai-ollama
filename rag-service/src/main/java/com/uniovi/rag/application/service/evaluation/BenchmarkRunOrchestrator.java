package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.EvaluationRunType;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.evaluation.EvaluationStudyType;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.service.async.AsyncTaskService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Creates {@link EvaluationRunEntity} rows and enqueues {@link AsyncTaskEntity} work for lab benchmarks.
 */
@Service
public class BenchmarkRunOrchestrator {

    private final UserRepository userRepository;
    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final RagPresetRepository ragPresetRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskService asyncTaskService;
    private final ProjectAccessService projectAccessService;
    private final RagRuntimeProperties ragRuntimeProperties;
    private final EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser;

    public BenchmarkRunOrchestrator(
            UserRepository userRepository,
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationCampaignRepository evaluationCampaignRepository,
            EvaluationRunRepository evaluationRunRepository,
            ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            RagPresetRepository ragPresetRepository,
            AsyncTaskRepository asyncTaskRepository,
            AsyncTaskService asyncTaskService,
            ProjectAccessService projectAccessService,
            RagRuntimeProperties ragRuntimeProperties,
            EvaluationDatasetStorePort evaluationDatasetStorePort,
            EvaluationWorkbookParser evaluationWorkbookParser) {
        this.userRepository = userRepository;
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.ragPresetRepository = ragPresetRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskService = asyncTaskService;
        this.projectAccessService = projectAccessService;
        this.ragRuntimeProperties = ragRuntimeProperties;
        this.evaluationDatasetStorePort = evaluationDatasetStorePort;
        this.evaluationWorkbookParser = evaluationWorkbookParser;
    }

    @Transactional
    public BenchmarkJobAccepted startJsonBenchmark(
            UUID userId,
            String roleName,
            BenchmarkKind kind,
            StartBenchmarkRunRequest request) {
        // Multi-model campaign (LLM baseline): create a campaign + multiple child runs, then enqueue each child.
        if (kind == BenchmarkKind.LLM_JUDGE_QA && wantsLlmCampaign(request)) {
            return startLlmCampaign(userId, roleName, kind, request);
        }
        // Multi-model embedding campaign: explicitly blocked unless we can truly swap embedding runtime.
        if (kind == BenchmarkKind.EMBEDDING_RETRIEVAL && wantsEmbeddingCampaign(request)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "EMBEDDING_RUNTIME_SWAP_NOT_IMPLEMENTED: multi-embedding campaigns require true embedding-model swap + reindex/snapshot isolation");
        }
        // RAG preset sweep campaign: group one run under a campaign so exports/comparison can be campaign-scoped.
        if (kind == BenchmarkKind.RAG_PRESET_END_TO_END && wantsRagPresetCampaign(request)) {
            return startRagPresetCampaign(userId, roleName, kind, request);
        }

        validateRunKind(roleName, request.runKind());
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateScienceFields(kind, request);

        EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, request);
        run.setName(request.name() != null ? request.name() : kind.name());
        applyOptionalLinks(run, request);

        run = evaluationRunRepository.save(run);

        UUID taskId =
                switch (kind) {
                    case LLM_JUDGE_QA -> asyncTaskService.submitEvalLlm(userId, request.projectId(), run.getId());
                    case RAG_PRESET_END_TO_END -> asyncTaskService.submitEvalRag(userId, request.projectId(), run.getId());
                    case EMBEDDING_RETRIEVAL -> asyncTaskService.submitEvalEmbeddingRetrieval(
                            userId, request.projectId(), run.getId());
                    case CLASSIFIER_METRICS ->
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "Use multipart endpoint for CLASSIFIER_METRICS");
                };

        attachTaskAndRunning(run, taskId);
        return BenchmarkJobAccepted.of(run.getId(), taskId);
    }

    private BenchmarkJobAccepted startRagPresetCampaign(
            UUID userId, String roleName, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        validateRunKind(roleName, request.runKind());
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateScienceFields(kind, request);
        if (request.experimentalPresetCodes() == null || request.experimentalPresetCodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentalPresetCodes is empty");
        }

        UserEntity user = userRepository.findById(userId).orElseThrow();
        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setUser(user);
        camp.setCreatedAt(Instant.now());
        camp.setStudyType(EvaluationStudyType.RAG_PRESET_BENCHMARK.name());
        camp.setName(request.campaignName() != null && !request.campaignName().isBlank()
                ? request.campaignName().trim()
                : "RAG preset sweep");
        if (request.projectId() != null) {
            ProjectEntity p = projectAccessService.requireOwnedProject(userId, request.projectId());
            camp.setProject(p);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.getId().toString());
        meta.put("benchmarkKind", kind.name());
        meta.put("experimentalPresetCodes", request.experimentalPresetCodes());
        camp.setMetaJson(meta);
        camp = evaluationCampaignRepository.save(camp);

        EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, request);
        run.setCampaign(camp);
        run.setName(request.name() != null && !request.name().isBlank() ? request.name().trim() : "RAG preset sweep");
        applyOptionalLinks(run, request);
        run = evaluationRunRepository.save(run);

        UUID taskId = asyncTaskService.submitEvalRag(userId, request.projectId(), run.getId());
        attachTaskAndRunning(run, taskId);
        return BenchmarkJobAccepted.ofCampaign(run.getId(), taskId, camp.getId());
    }

    private BenchmarkJobAccepted startLlmCampaign(
            UUID userId, String roleName, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        validateRunKind(roleName, request.runKind());
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateScienceFields(kind, request);

        List<String> modelIds = resolveLlmCandidateModelIds(dataset, request);
        if (modelIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "llmModelIds is empty");
        }

        UserEntity user = userRepository.findById(userId).orElseThrow();
        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setUser(user);
        camp.setCreatedAt(Instant.now());
        camp.setStudyType(EvaluationStudyType.LLM_MODEL_BASELINE.name());
        if (request.campaignName() != null && !request.campaignName().isBlank()) {
            camp.setName(request.campaignName().trim());
        } else {
            camp.setName("LLM baseline campaign");
        }
        if (request.projectId() != null) {
            ProjectEntity p = projectAccessService.requireOwnedProject(userId, request.projectId());
            camp.setProject(p);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.getId().toString());
        meta.put("benchmarkKind", kind.name());
        meta.put("llmModelIds", modelIds);
        meta.put("useWorkbookCandidates", request.useWorkbookCandidatesEffective());
        camp.setMetaJson(meta);
        camp = evaluationCampaignRepository.save(camp);

        UUID firstRunId = null;
        UUID lastTaskId = null;
        for (String modelId : modelIds) {
            StartBenchmarkRunRequest childReq =
                    new StartBenchmarkRunRequest(
                            request.datasetId(),
                            request.projectId(),
                            request.runKind(),
                            request.name(),
                            request.resolvedConfigSnapshotId(),
                            request.indexSnapshotId(),
                            request.presetId(),
                            request.embeddingDownstreamRag(),
                            request.experimentalPresetCodes(),
                            modelId,
                            request.embeddingModelId(),
                            List.of(),
                            List.of(),
                            false,
                            null);
            EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, childReq);
            run.setCampaign(camp);
            run.setName(childRunName(request.name(), kind, modelId));
            applyOptionalLinks(run, childReq);
            run = evaluationRunRepository.save(run);

            UUID taskId = asyncTaskService.submitEvalLlm(userId, request.projectId(), run.getId());
            attachTaskAndRunning(run, taskId);

            if (firstRunId == null) {
                firstRunId = run.getId();
            }
            lastTaskId = taskId;
        }

        // For backward compatibility, return the first runId + last taskId; expose campaignId explicitly.
        // Clients should use campaignId to list/compare/export across the grouped runs.
        return BenchmarkJobAccepted.ofCampaign(firstRunId, lastTaskId, camp.getId());
    }

    private static boolean wantsLlmCampaign(StartBenchmarkRunRequest request) {
        return (request.llmModelIds() != null && !request.llmModelIds().isEmpty()) || request.useWorkbookCandidatesEffective();
    }

    private static boolean wantsEmbeddingCampaign(StartBenchmarkRunRequest request) {
        return (request.embeddingModelIds() != null && !request.embeddingModelIds().isEmpty()) || request.useWorkbookCandidatesEffective();
    }

    private static boolean wantsRagPresetCampaign(StartBenchmarkRunRequest request) {
        return request.campaignName() != null
                && !request.campaignName().isBlank()
                && request.experimentalPresetCodes() != null
                && !request.experimentalPresetCodes().isEmpty();
    }

    private List<String> resolveLlmCandidateModelIds(EvaluationDatasetEntity dataset, StartBenchmarkRunRequest request) {
        if (request.llmModelIds() != null && !request.llmModelIds().isEmpty()) {
            return request.llmModelIds();
        }
        if (!request.useWorkbookCandidatesEffective()) {
            return List.of();
        }
        EvaluationWorkbook wb = parseWorkbookForCandidates(dataset);
        List<String> out = new ArrayList<>();
        wb.llmCandidates().forEach(c -> {
            String model = c.model() != null && !c.model().isBlank() ? c.model().trim() : null;
            if (model != null && !model.isBlank()) {
                out.add(model);
            }
        });
        return out;
    }

    private EvaluationWorkbook parseWorkbookForCandidates(EvaluationDatasetEntity ds) {
        ExperimentalDatasetType experimental = BenchmarkDatasetCompatibility.resolveExperimentalType(ds);
        try (InputStream in = openDatasetStream(ds)) {
            WorkbookParseResult parsed = evaluationWorkbookParser.parse(in, experimental);
            if (parsed.validationReport().hasErrors()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Dataset workbook is INVALID for campaign candidate resolution");
            }
            return parsed.workbook();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to open dataset workbook bytes for candidate resolution");
        }
    }

    private InputStream openDatasetStream(EvaluationDatasetEntity ds) throws IOException {
        String uri = ds.getStorageUri();
        if (uri == null || uri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "evaluation_dataset.storage_uri is missing");
        }
        String trimmed = uri.trim();
        if (trimmed.startsWith(ExperimentalDatasetResolver.CLASSPATH_STORAGE_PREFIX)) {
            String path = trimmed.substring(ExperimentalDatasetResolver.CLASSPATH_STORAGE_PREFIX.length());
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Classpath dataset missing: " + path);
            }
            return resource.getInputStream();
        }
        return evaluationDatasetStorePort.openStream(trimmed);
    }

    private static String childRunName(String baseName, BenchmarkKind kind, String modelId) {
        String prefix = baseName != null && !baseName.isBlank() ? baseName.trim() : kind.name();
        return prefix + " — model " + modelId;
    }

    @Transactional
    public BenchmarkJobAccepted startClassifierMetrics(
            UUID userId,
            String roleName,
            StartBenchmarkRunRequest meta,
            String modelId,
            boolean includeImages,
            MultipartFile datasetFile)
            throws IOException {
        validateRunKind(roleName, meta.runKind());
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, meta.datasetId());
        if (dataset.getType() != EvaluationDatasetType.CLASSIFIER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dataset type must be CLASSIFIER");
        }
        ExperimentalDatasetType exp = BenchmarkDatasetCompatibility.resolveExperimentalType(dataset);
        if (!BenchmarkDatasetCompatibility.compatible(exp, BenchmarkKind.CLASSIFIER_METRICS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dataset experimental kind must be CLASSIFIER_DATASET for CLASSIFIER_METRICS");
        }
        validateScienceFields(BenchmarkKind.CLASSIFIER_METRICS, meta);

        EvaluationRunEntity run = baseRun(userId, meta.projectId(), dataset, BenchmarkKind.CLASSIFIER_METRICS, meta);
        run.setName(meta.name() != null ? meta.name() : "CLASSIFIER_METRICS");
        if (modelId != null && !modelId.isBlank()) {
            run.setClassifierModelId(modelId.trim());
        }
        applyOptionalLinks(run, meta);
        run = evaluationRunRepository.save(run);

        UUID taskId = asyncTaskService.submitClassifierEval(
                userId, meta.projectId(), modelId, includeImages, datasetFile, run.getId());
        attachTaskAndRunning(run, taskId);
        return BenchmarkJobAccepted.of(run.getId(), taskId);
    }

    private void validateRunKind(String roleName, EvaluationRunKind runKind) {
        if (runKind == EvaluationRunKind.ADMIN_BASELINE
                && !UserRole.ADMIN.name().equalsIgnoreCase(roleName != null ? roleName : "")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_BASELINE requires ADMIN");
        }
    }

    private EvaluationDatasetEntity loadAndAuthorizeDataset(UUID userId, String roleName, UUID datasetId) {
        EvaluationDatasetEntity ds =
                evaluationDatasetRepository.findById(datasetId).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dataset not found"));
        if (EvaluationDatasetScope.SYSTEM_DATASET.name().equals(ds.getDatasetScope())) {
            if (!UserRole.ADMIN.name().equalsIgnoreCase(roleName != null ? roleName : "")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM_DATASET requires ADMIN");
            }
        } else if (ds.getOwner() == null || !userId.equals(ds.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dataset not owned by user");
        }
        return ds;
    }

    private static void validateDatasetForKind(EvaluationDatasetEntity dataset, BenchmarkKind kind) {
        if (kind == BenchmarkKind.CLASSIFIER_METRICS) {
            return;
        }
        ExperimentalDatasetType experimental = BenchmarkDatasetCompatibility.resolveExperimentalType(dataset);
        if (!BenchmarkDatasetCompatibility.compatible(experimental, kind)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dataset experimental kind "
                            + experimental
                            + " is incompatible with benchmark "
                            + kind
                            + ". Example: LLM_JUDGE_QA requires LLM_MODEL_BASELINE or REFERENCE_BUNDLE.");
        }
    }

    private void validateScienceFields(BenchmarkKind kind, StartBenchmarkRunRequest request) {
        if (request.runKind() != EvaluationRunKind.SCIENCE) {
            return;
        }
        if (request.resolvedConfigSnapshotId() == null
                && (kind == BenchmarkKind.LLM_JUDGE_QA || kind == BenchmarkKind.RAG_PRESET_END_TO_END)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "resolvedConfigSnapshotId is required for SCIENCE runs with config");
        }
        if (kind == BenchmarkKind.EMBEDDING_RETRIEVAL && request.indexSnapshotId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "indexSnapshotId is required for EMBEDDING_RETRIEVAL");
        }
        if (kind == BenchmarkKind.RAG_PRESET_END_TO_END && request.presetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presetId is required for RAG_PRESET_END_TO_END SCIENCE");
        }
    }

    private EvaluationRunEntity baseRun(
            UUID userId,
            UUID projectId,
            EvaluationDatasetEntity dataset,
            BenchmarkKind kind,
            StartBenchmarkRunRequest request) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setUser(user);
        run.setDataset(dataset);
        run.setType(mapRunType(kind));
        run.setConfigIds(List.of());
        run.setStatus(EvaluationRunStatus.PENDING);
        run.setProgress(0);
        run.setCreatedAt(Instant.now());
        run.setBenchmarkKind(kind.name());
        run.setRunKind(request.runKind().name());
        run.setDatasetSha256(dataset.getSha256());
        run.setWorkflowSchemaVersion(ragRuntimeProperties.getWorkflowSchemaVersion());
        if (projectId != null) {
            ProjectEntity p = projectAccessService.requireOwnedProject(userId, projectId);
            run.setProject(p);
        }
        return run;
    }

    private void applyOptionalLinks(EvaluationRunEntity run, StartBenchmarkRunRequest request) {
        if (request.resolvedConfigSnapshotId() != null) {
            ResolvedConfigSnapshotEntity snap =
                    resolvedConfigSnapshotRepository.findById(request.resolvedConfigSnapshotId()).orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolvedConfigSnapshotId not found"));
            run.setResolvedConfigSnapshot(snap);
        }
        if (request.indexSnapshotId() != null) {
            KnowledgeIndexSnapshotEntity idx =
                    knowledgeIndexSnapshotRepository.findById(request.indexSnapshotId()).orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "indexSnapshotId not found"));
            run.setIndexSnapshot(idx);
            if (idx.getSignatureHash() != null) {
                run.setIndexSignatureHash(idx.getSignatureHash());
            }
        }
        if (request.presetId() != null) {
            RagPresetEntity preset =
                    ragPresetRepository.findById(request.presetId()).orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "presetId not found"));
            run.setPreset(preset);
        }
        run.setEmbeddingDownstreamRag(request.embeddingDownstreamRagEffective());
        if (!request.experimentalPresetCodes().isEmpty()) {
            run.setAggregatesJson(Map.of("requested_preset_codes", request.experimentalPresetCodes()));
        }
        if (request.llmModelId() != null && !request.llmModelId().isBlank()) {
            run.setLlmModelId(request.llmModelId().trim());
        }
        if (request.embeddingModelId() != null && !request.embeddingModelId().isBlank()) {
            run.setEmbeddingModelId(request.embeddingModelId().trim());
        }
    }

    private void attachTaskAndRunning(EvaluationRunEntity run, UUID taskId) {
        AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElseThrow();
        run.setAsyncTask(task);
        run.setStatus(EvaluationRunStatus.RUNNING);
        evaluationRunRepository.save(run);
    }

    private static EvaluationRunType mapRunType(BenchmarkKind kind) {
        return switch (kind) {
            case LLM_JUDGE_QA -> EvaluationRunType.LLM_ONLY;
            case RAG_PRESET_END_TO_END -> EvaluationRunType.RAG_FULL;
            case EMBEDDING_RETRIEVAL -> EvaluationRunType.RAG_FULL;
            case CLASSIFIER_METRICS -> EvaluationRunType.CLASSIFIER;
        };
    }
}
