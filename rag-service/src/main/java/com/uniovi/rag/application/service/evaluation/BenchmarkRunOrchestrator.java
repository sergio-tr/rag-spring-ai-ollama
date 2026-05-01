package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.EvaluationRunStatus;
import com.uniovi.rag.domain.EvaluationRunType;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates {@link EvaluationRunEntity} rows and enqueues {@link AsyncTaskEntity} work for lab benchmarks.
 */
@Service
public class BenchmarkRunOrchestrator {

    private final UserRepository userRepository;
    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final RagPresetRepository ragPresetRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskService asyncTaskService;
    private final ProjectAccessService projectAccessService;
    private final RagRuntimeProperties ragRuntimeProperties;

    public BenchmarkRunOrchestrator(
            UserRepository userRepository,
            EvaluationDatasetRepository evaluationDatasetRepository,
            EvaluationRunRepository evaluationRunRepository,
            ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            RagPresetRepository ragPresetRepository,
            AsyncTaskRepository asyncTaskRepository,
            AsyncTaskService asyncTaskService,
            ProjectAccessService projectAccessService,
            RagRuntimeProperties ragRuntimeProperties) {
        this.userRepository = userRepository;
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.ragPresetRepository = ragPresetRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskService = asyncTaskService;
        this.projectAccessService = projectAccessService;
        this.ragRuntimeProperties = ragRuntimeProperties;
    }

    @Transactional
    public BenchmarkJobAccepted startJsonBenchmark(
            UUID userId,
            String roleName,
            BenchmarkKind kind,
            StartBenchmarkRunRequest request) {
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
        switch (kind) {
            case LLM_JUDGE_QA, RAG_PRESET_END_TO_END, EMBEDDING_RETRIEVAL -> {
                if (dataset.getType() != EvaluationDatasetType.RAG && dataset.getType() != EvaluationDatasetType.LLM_ONLY) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Dataset type must be RAG or LLM_ONLY for this benchmark");
                }
            }
            case CLASSIFIER_METRICS -> {
                if (dataset.getType() != EvaluationDatasetType.CLASSIFIER) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dataset type must be CLASSIFIER");
                }
            }
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
