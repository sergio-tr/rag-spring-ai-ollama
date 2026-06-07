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
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingCandidate;
import com.uniovi.rag.domain.evaluation.workbook.WorkbookParseResult;
import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.evaluation.workbook.LabDatasetGateValidator;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightResult;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapErrors;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.evaluation.workbook.ValidationReport;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates {@link EvaluationRunEntity} rows and enqueues {@link AsyncTaskEntity} work for lab benchmarks.
 */
@Service
public class BenchmarkRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunOrchestrator.class);

    static final String AGG_KEY_REQUESTED_PRESET_CODES = "requested_preset_codes";
    static final String AGG_KEY_AUTO_REINDEX_POLICY = "autoReindexPolicy";
    static final String AGG_KEY_AUTO_REINDEX_LOCK_ACQUIRED = "autoReindexLockAcquired";
    static final String AGG_KEY_AUTO_REINDEX_MODE = "autoReindexMode";
    static final String AGG_KEY_AUTO_REINDEX_WARNING = "autoReindexWarning";
    static final String AGG_KEY_CORPUS_BOOTSTRAP_POLICY = "corpusBootstrapPolicy";
    static final String AGG_KEY_CORPUS_READINESS = "corpusReadiness";
    static final String AGG_KEY_CONFIG_PREFLIGHT = "configPreflight";

    private final UserRepository userRepository;
    private final EvaluationDatasetRepository evaluationDatasetRepository;
    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final RagPresetRepository ragPresetRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskService asyncTaskService;
    private final LabJobLifecycleService labJobLifecycleService;
    private final ProjectAccessService projectAccessService;
    private final RagRuntimeProperties ragRuntimeProperties;
    private final EvaluationDatasetStorePort evaluationDatasetStorePort;
    private final EvaluationWorkbookParser evaluationWorkbookParser;
    private final EmbeddingSpaceGuard embeddingSpaceGuard;
    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final EvaluationCorpusReadinessService evaluationCorpusReadinessService;
    private final EvaluationCorpusRepository evaluationCorpusRepository;
    private final LabBenchmarkConfigPreflightService labBenchmarkConfigPreflightService;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    @Autowired(required = false)
    private EmbeddingCampaignSnapshotAlignmentService embeddingCampaignSnapshotAlignmentService;

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
            LabJobLifecycleService labJobLifecycleService,
            ProjectAccessService projectAccessService,
            RagRuntimeProperties ragRuntimeProperties,
            EvaluationDatasetStorePort evaluationDatasetStorePort,
            EvaluationWorkbookParser evaluationWorkbookParser,
            EmbeddingSpaceGuard embeddingSpaceGuard,
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            EvaluationCorpusReadinessService evaluationCorpusReadinessService,
            EvaluationCorpusRepository evaluationCorpusRepository,
            LabBenchmarkConfigPreflightService labBenchmarkConfigPreflightService,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.userRepository = userRepository;
        this.evaluationDatasetRepository = evaluationDatasetRepository;
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.ragPresetRepository = ragPresetRepository;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskService = asyncTaskService;
        this.labJobLifecycleService = labJobLifecycleService;
        this.projectAccessService = projectAccessService;
        this.ragRuntimeProperties = ragRuntimeProperties;
        this.evaluationDatasetStorePort = evaluationDatasetStorePort;
        this.evaluationWorkbookParser = evaluationWorkbookParser;
        this.embeddingSpaceGuard = embeddingSpaceGuard;
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.evaluationCorpusReadinessService = evaluationCorpusReadinessService;
        this.evaluationCorpusRepository = evaluationCorpusRepository;
        this.labBenchmarkConfigPreflightService = labBenchmarkConfigPreflightService;
        this.runtimeObservability = runtimeObservability;
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
        if (kind == BenchmarkKind.EMBEDDING_RETRIEVAL && wantsEmbeddingCampaign(request)) {
            return startEmbeddingCampaign(userId, roleName, kind, request);
        }
        // RAG preset sweep campaign: group one run under a campaign so exports/comparison can be campaign-scoped.
        if (kind == BenchmarkKind.RAG_PRESET_END_TO_END && wantsRagPresetCampaign(request)) {
            validateAutoReindexRequest(kind, request);
            return startRagPresetCampaign(userId, roleName, kind, request);
        }

        validateRunKind(roleName, request.runKind());
        validateAutoReindexRequest(kind, request);
        validateClasspathCorpusBootstrapRequest(kind, request);
        validateDocumentBackedCorpus(userId, kind, request);
        LabBenchmarkConfigPreflightResult configPreflight =
                validateAndRecordConfigPreflight(userId, kind, request);
        requireNoActiveLabJob(userId, resolveConcurrencyScopeId(userId, request));
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateDatasetPreRunEligibility(kind, dataset);
        validateScienceFields(kind, request);

        EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, request);
        run.setName(request.name() != null ? request.name() : kind.name());
        applyCorpusLinks(userId, run, request);
        applyOptionalLinks(run, request);
        applyCorpusReadinessAggregates(userId, run, request);
        applyConfigPreflightAggregates(run, configPreflight);
        finalizeEmbeddingRetrievalRuntimeBinding(kind, run);

        run = evaluationRunRepository.save(run);

        UUID scopeProjectId = run.getProject() != null ? run.getProject().getId() : null;
        UUID taskId =
                switch (kind) {
                    case LLM_JUDGE_QA -> asyncTaskService.submitEvalLlm(userId, scopeProjectId, run.getId());
                    case RAG_PRESET_END_TO_END -> asyncTaskService.submitEvalRag(userId, scopeProjectId, run.getId());
                    case EMBEDDING_RETRIEVAL -> asyncTaskService.submitEvalEmbeddingRetrieval(
                            userId, scopeProjectId, run.getId());
                    case CLASSIFIER_METRICS ->
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "Use multipart endpoint for CLASSIFIER_METRICS");
                };

        attachTaskAndRunning(run, taskId);
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            obs.labRunAccepted(
                    run.getId(),
                    taskId,
                    kind != null ? kind.name() : null,
                    primarySnapshotId(request));
        }
        return BenchmarkJobAccepted.of(run.getId(), taskId);
    }

    private LabBenchmarkConfigPreflightResult validateAndRecordConfigPreflight(
            UUID userId, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        try {
            LabBenchmarkConfigPreflightResult result =
                    labBenchmarkConfigPreflightService.validateOrThrow(userId, kind, request);
            recordLabConfigPreflight(kind, request, result);
            return result;
        } catch (ResponseStatusException ex) {
            recordLabConfigPreflightFailure(kind, request, ex.getReason());
            throw ex;
        }
    }

    private void recordLabConfigPreflight(
            BenchmarkKind kind, StartBenchmarkRunRequest request, LabBenchmarkConfigPreflightResult configPreflight) {
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs == null) {
            return;
        }
        String presetKey = resolvePresetKey(request);
        String reasonCode =
                configPreflight != null && configPreflight.primaryCode() != null
                        ? configPreflight.primaryCode()
                        : "OK";
        obs.labConfigPreflight(presetKey, reasonCode, kind != null ? kind.name() : null);
    }

    private void recordLabConfigPreflightFailure(
            BenchmarkKind kind, StartBenchmarkRunRequest request, String configReasonCode) {
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs == null) {
            return;
        }
        String code = configReasonCode != null && !configReasonCode.isBlank() ? configReasonCode : "CONFIG_VALIDATION_ERROR";
        obs.labConfigPreflight(resolvePresetKey(request), code, kind != null ? kind.name() : null);
    }

    private static String resolvePresetKey(StartBenchmarkRunRequest request) {
        if (request != null
                && request.experimentalPresetCodes() != null
                && !request.experimentalPresetCodes().isEmpty()) {
            return request.experimentalPresetCodes().getFirst();
        }
        return null;
    }

    private static UUID primarySnapshotId(StartBenchmarkRunRequest request) {
        if (request == null) {
            return null;
        }
        if (request.indexSnapshotId() != null) {
            return request.indexSnapshotId();
        }
        if (request.indexSnapshotIds() != null && !request.indexSnapshotIds().isEmpty()) {
            return request.indexSnapshotIds().getFirst();
        }
        return null;
    }

    private void validateDatasetPreRunEligibility(BenchmarkKind kind, EvaluationDatasetEntity dataset) {
        if (kind == null || dataset == null) {
            return;
        }
        if (kind == BenchmarkKind.CLASSIFIER_METRICS) {
            return;
        }
        ExperimentalDatasetType experimental = BenchmarkDatasetCompatibility.resolveExperimentalType(dataset);
        try (InputStream in = openDatasetStream(dataset)) {
            WorkbookParseResult parsed = evaluationWorkbookParser.parse(in, experimental);
            if (parsed.validationReport().hasErrors()) {
                throw new LabDatasetGateException(
                        "EXPERIMENTAL_DATASET_INVALID",
                        "Dataset workbook failed validation; fix validationIssues before running a benchmark.",
                        parsed.validationReport());
            }
            var gate = new ValidationReport();
            LabDatasetGateValidator.validatePreRun(kind, experimental, parsed.workbook(), gate);
            if (gate.hasErrors()) {
                String primary = gate.issues().isEmpty() ? "DATASET_INVALID" : gate.issues().getFirst().code().name();
                throw new LabDatasetGateException(
                        primary,
                        "Dataset is not eligible for " + kind.name() + " (see validationIssues).",
                        gate);
            }
        } catch (IOException e) {
            throw new LabDatasetGateException(
                    "WORKBOOK_IO_ERROR",
                    "Failed to open dataset workbook bytes for validation",
                    new ValidationReport());
        }
    }

    private BenchmarkJobAccepted startRagPresetCampaign(
            UUID userId, String roleName, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        validateRunKind(roleName, request.runKind());
        validateAutoReindexRequest(kind, request);
        validateClasspathCorpusBootstrapRequest(kind, request);
        validateDocumentBackedCorpus(userId, kind, request);
        LabBenchmarkConfigPreflightResult configPreflight =
                validateAndRecordConfigPreflight(userId, kind, request);
        requireNoActiveLabJob(userId, resolveConcurrencyScopeId(userId, request));
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateScienceFields(kind, request);
        List<String> presetCodes = request.experimentalPresetCodes();

        UserEntity user = userRepository.findById(userId).orElseThrow();
        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setUser(user);
        camp.setCreatedAt(Instant.now());
        camp.setStudyType(EvaluationStudyType.RAG_PRESET_BENCHMARK.name());
        camp.setName(request.campaignName() != null && !request.campaignName().isBlank()
                ? request.campaignName().trim()
                : "RAG preset comparison");
        if (request.projectId() != null) {
            ProjectEntity p = projectAccessService.requireOwnedProject(userId, request.projectId());
            camp.setProject(p);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.getId().toString());
        meta.put("benchmarkKind", kind.name());
        meta.put("experimentalPresetCodes", presetCodes);
        meta.put("comparativeMode", presetCodes.size() >= 2);
        int perRunItems = resolveDatasetItemCount(dataset, kind);
        int plannedTotalItems = perRunItems * presetCodes.size();
        meta.put("perAxisItemCount", perRunItems);
        meta.put("plannedTotalItems", plannedTotalItems);
        camp.setMetaJson(meta);
        camp = evaluationCampaignRepository.save(camp);

        UUID firstRunId = null;
        for (String presetCode : presetCodes) {
            StartBenchmarkRunRequest childReq =
                    new StartBenchmarkRunRequest(
                            request.datasetId(),
                            request.corpusId(),
                            request.projectId(),
                            request.runKind(),
                            request.name(),
                            request.resolvedConfigSnapshotId(),
                            request.indexSnapshotId(),
                            request.presetId(),
                            request.embeddingDownstreamRag(),
                            List.of(presetCode),
                            request.llmModelId(),
                            request.embeddingModelId(),
                            List.of(),
                            List.of(),
                            false,
                            null,
                            request.autoReindex(),
                            request.allowActiveSnapshotMutation(),
                            request.reuseCompatibleActiveSnapshot(),
                            request.failOnReindexFailure(),
                            request.bootstrapCorpusFromClasspathDocs(),
                            request.classpathDocsLocation(),
                            request.bootstrapCorpusScope(),
                            request.bootstrapSkipExisting(),
                            request.bootstrapFailOnDocumentError(),
                            List.of());
            EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, childReq);
            run.setCampaign(camp);
            run.setName(childRunName(request.name(), kind, presetCode));
            applyCorpusLinks(userId, run, childReq);
            applyOptionalLinks(run, childReq);
            applyCorpusReadinessAggregates(userId, run, childReq);
            applyConfigPreflightAggregates(run, configPreflight);
            run = evaluationRunRepository.save(run);

            if (firstRunId == null) {
                firstRunId = run.getId();
            }
        }
        EvaluationRunEntity coordinator =
                evaluationRunRepository.findById(firstRunId).orElseThrow();
        UUID scopeProjectId = coordinator.getProject() != null ? coordinator.getProject().getId() : null;
        UUID taskId = asyncTaskService.submitEvalRagCampaign(userId, scopeProjectId, camp.getId(), firstRunId);
        attachTaskAndRunning(coordinator, taskId);
        return BenchmarkJobAccepted.ofCampaign(firstRunId, taskId, camp.getId(), plannedTotalItems);
    }

    private BenchmarkJobAccepted startLlmCampaign(
            UUID userId, String roleName, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        validateRunKind(roleName, request.runKind());
        validateClasspathCorpusBootstrapRequest(kind, request);
        requireNoActiveLabJob(userId, resolveConcurrencyScopeId(userId, request));
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
        meta.put("comparativeMode", modelIds.size() >= 2);
        int perRunItems = resolveDatasetItemCount(dataset, kind);
        int plannedTotalItems = perRunItems * modelIds.size();
        meta.put("perAxisItemCount", perRunItems);
        meta.put("plannedTotalItems", plannedTotalItems);
        camp.setMetaJson(meta);
        camp = evaluationCampaignRepository.save(camp);

        UUID firstRunId = null;
        for (String modelId : modelIds) {
            StartBenchmarkRunRequest childReq =
                    new StartBenchmarkRunRequest(
                            request.datasetId(),
                            request.corpusId(),
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
                            null,
                            request.autoReindex(),
                            request.allowActiveSnapshotMutation(),
                            request.reuseCompatibleActiveSnapshot(),
                            request.failOnReindexFailure(),
                            request.bootstrapCorpusFromClasspathDocs(),
                            request.classpathDocsLocation(),
                            request.bootstrapCorpusScope(),
                            request.bootstrapSkipExisting(),
                            request.bootstrapFailOnDocumentError(),
                            List.of());
            EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, childReq);
            run.setCampaign(camp);
            run.setName(childRunName(request.name(), kind, modelId));
            applyCorpusLinks(userId, run, childReq);
            applyOptionalLinks(run, childReq);
            run = evaluationRunRepository.save(run);

            if (firstRunId == null) {
                firstRunId = run.getId();
            }
        }
        EvaluationRunEntity coordinator =
                evaluationRunRepository.findById(firstRunId).orElseThrow();
        UUID scopeProjectId = coordinator.getProject() != null ? coordinator.getProject().getId() : null;
        UUID taskId = asyncTaskService.submitEvalLlmCampaign(userId, scopeProjectId, camp.getId(), firstRunId);
        attachTaskAndRunning(coordinator, taskId);
        return BenchmarkJobAccepted.ofCampaign(firstRunId, taskId, camp.getId(), plannedTotalItems);
    }

    private BenchmarkJobAccepted startEmbeddingCampaign(
            UUID userId, String roleName, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        validateRunKind(roleName, request.runKind());
        validateClasspathCorpusBootstrapRequest(kind, request);
        validateDocumentBackedCorpus(userId, kind, request);
        validateAndRecordConfigPreflight(userId, kind, request);
        requireNoActiveLabJob(userId, resolveConcurrencyScopeId(userId, request));
        EvaluationDatasetEntity dataset = loadAndAuthorizeDataset(userId, roleName, request.datasetId());
        validateDatasetForKind(dataset, kind);
        validateDatasetPreRunEligibility(kind, dataset);
        validateScienceFields(kind, request);

        List<String> modelIds = resolveEmbeddingCandidateModelIds(dataset, request);
        if (modelIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embeddingModelIds is empty");
        }
        UUID alignProjectId = resolveEmbeddingAlignProjectId(userId, request);
        List<UUID> alignedIndexSnapshotIds =
                embeddingCampaignSnapshotAlignmentService != null
                        ? embeddingCampaignSnapshotAlignmentService.alignFromCatalog(
                                alignProjectId, request.corpusId(), modelIds, request.indexSnapshotIds())
                        : alignEmbeddingCampaignIndexSnapshotIds(
                                alignProjectId, request.corpusId(), modelIds, request.indexSnapshotIds());
        if (modelIds.size() >= 2
                && embeddingCampaignSnapshotAlignmentService != null
                && request.corpusId() != null) {
            alignedIndexSnapshotIds =
                    embeddingCampaignSnapshotAlignmentService.ensureAligned(
                            userId,
                            alignProjectId,
                            request.corpusId(),
                            modelIds,
                            alignedIndexSnapshotIds);
        }
        if (modelIds.size() > 1 && alignedIndexSnapshotIds.size() != modelIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "EMBEDDING_CAMPAIGN_REQUIRES_ALIGNED_INDEX_SNAPSHOT_IDS: indexSnapshotIds must match embeddingModelIds length");
        }
        if (modelIds.size() > 1) {
            for (int i = 0; i < modelIds.size(); i++) {
                if (i >= alignedIndexSnapshotIds.size() || alignedIndexSnapshotIds.get(i) == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "EMBEDDING_CAMPAIGN_MISSING_INDEX_SNAPSHOT: no project index snapshot for embedding model "
                                    + modelIds.get(i)
                                    + ". Prepare or rebuild the project index for each selected model before running a comparison.");
                }
            }
        }

        UserEntity user = userRepository.findById(userId).orElseThrow();
        EvaluationCampaignEntity camp = new EvaluationCampaignEntity();
        camp.setUser(user);
        camp.setCreatedAt(Instant.now());
        camp.setStudyType(EvaluationStudyType.EMBEDDING_MODEL_BASELINE.name());
        if (request.campaignName() != null && !request.campaignName().isBlank()) {
            camp.setName(request.campaignName().trim());
        } else {
            camp.setName("Embedding model campaign");
        }
        if (request.projectId() != null) {
            ProjectEntity p = projectAccessService.requireOwnedProject(userId, request.projectId());
            camp.setProject(p);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("datasetId", dataset.getId().toString());
        meta.put("benchmarkKind", kind.name());
        meta.put("embeddingModelIds", modelIds);
        meta.put(
                "indexSnapshotIds",
                alignedIndexSnapshotIds.stream().filter(Objects::nonNull).map(UUID::toString).toList());
        meta.put("useWorkbookCandidates", request.useWorkbookCandidatesEffective());
        meta.put("comparativeMode", modelIds.size() >= 2);
        int perRunItems = resolveDatasetItemCount(dataset, kind);
        int plannedTotalItems = perRunItems * modelIds.size();
        meta.put("perAxisItemCount", perRunItems);
        meta.put("plannedTotalItems", plannedTotalItems);
        camp.setMetaJson(meta);
        camp = evaluationCampaignRepository.save(camp);

        UUID firstRunId = null;
        for (int i = 0; i < modelIds.size(); i++) {
            String modelId = modelIds.get(i);
            UUID childIndexSnapshotId = resolveAlignedIndexSnapshotId(request, alignedIndexSnapshotIds, i, modelIds.size());
            StartBenchmarkRunRequest childReq =
                    new StartBenchmarkRunRequest(
                            request.datasetId(),
                            request.corpusId(),
                            request.projectId(),
                            request.runKind(),
                            request.name(),
                            request.resolvedConfigSnapshotId(),
                            childIndexSnapshotId,
                            request.presetId(),
                            request.embeddingDownstreamRag(),
                            request.experimentalPresetCodes(),
                            request.llmModelId(),
                            modelId,
                            List.of(),
                            List.of(),
                            false,
                            null,
                            request.autoReindex(),
                            request.allowActiveSnapshotMutation(),
                            request.reuseCompatibleActiveSnapshot(),
                            request.failOnReindexFailure(),
                            request.bootstrapCorpusFromClasspathDocs(),
                            request.classpathDocsLocation(),
                            request.bootstrapCorpusScope(),
                            request.bootstrapSkipExisting(),
                            request.bootstrapFailOnDocumentError(),
                            List.of());
            EvaluationRunEntity run = baseRun(userId, request.projectId(), dataset, kind, childReq);
            run.setCampaign(camp);
            run.setName(childRunName(request.name(), kind, modelId));
            applyCorpusLinks(userId, run, childReq);
            applyOptionalLinks(run, childReq);
            finalizeEmbeddingRetrievalRuntimeBinding(kind, run);
            run = evaluationRunRepository.save(run);

            if (firstRunId == null) {
                firstRunId = run.getId();
            }
        }
        EvaluationRunEntity coordinator =
                evaluationRunRepository.findById(firstRunId).orElseThrow();
        UUID scopeProjectId = coordinator.getProject() != null ? coordinator.getProject().getId() : null;
        UUID taskId =
                asyncTaskService.submitEvalEmbeddingCampaign(userId, scopeProjectId, camp.getId(), firstRunId);
        attachTaskAndRunning(coordinator, taskId);
        return BenchmarkJobAccepted.ofCampaign(firstRunId, taskId, camp.getId(), plannedTotalItems);
    }

    private static UUID resolveAlignedIndexSnapshotId(
            StartBenchmarkRunRequest request, List<UUID> alignedIndexSnapshotIds, int index, int modelsCount) {
        if (modelsCount > 1) {
            return alignedIndexSnapshotIds.get(index);
        }
        if (!alignedIndexSnapshotIds.isEmpty()) {
            return alignedIndexSnapshotIds.getFirst();
        }
        return request.indexSnapshotId();
    }

    private UUID resolveEmbeddingAlignProjectId(UUID userId, StartBenchmarkRunRequest request) {
        if (request.projectId() != null) {
            return request.projectId();
        }
        if (request.corpusId() == null) {
            return null;
        }
        return evaluationCorpusRepository
                .findByIdAndOwner_Id(request.corpusId(), userId)
                .map(c -> c.getIndexProject() != null ? c.getIndexProject().getId() : null)
                .orElse(null);
    }

    private List<UUID> alignEmbeddingCampaignIndexSnapshotIds(
            UUID projectId, UUID corpusId, List<String> modelIds, List<UUID> provided) {
        if (modelIds.isEmpty()) {
            return List.of();
        }
        if (modelIds.size() > 1 && !provided.isEmpty()) {
            return provided;
        }
        if (modelIds.size() == 1 && !provided.isEmpty()) {
            return List.of(provided.getFirst());
        }
        List<KnowledgeIndexSnapshotEntity> corpusSnapshots =
                corpusId != null
                        ? knowledgeIndexSnapshotRepository.findByOwnerOrderByCreatedAtDesc(
                                KnowledgeSnapshotOwnerType.EVALUATION_CORPUS, corpusId)
                        : List.of();
        List<KnowledgeIndexSnapshotEntity> projectSnapshots =
                projectId != null
                        ? knowledgeIndexSnapshotRepository.findByProjectAndScopeProjectOrderByCreatedAtDesc(
                                projectId, KnowledgeSnapshotScopeType.PROJECT)
                        : List.of();
        List<UUID> aligned = new ArrayList<>();
        for (String modelId : modelIds) {
            UUID matched = findEmbeddingSnapshotForModel(corpusSnapshots, modelId);
            if (matched == null) {
                matched = findEmbeddingSnapshotForModel(projectSnapshots, modelId);
            }
            aligned.add(matched);
        }
        return aligned;
    }

    private static UUID findEmbeddingSnapshotForModel(
            List<KnowledgeIndexSnapshotEntity> snapshots, String modelId) {
        return snapshots.stream()
                .filter(
                        s ->
                                IndexProfileJsonSupport.readEmbeddingModelId(s.getIndexProfileJsonb())
                                        .map(
                                                prof ->
                                                        IndexProfileJsonSupport.normalizeEmbeddingKey(prof)
                                                                .equals(
                                                                        IndexProfileJsonSupport.normalizeEmbeddingKey(
                                                                                modelId)))
                                        .orElse(false))
                .map(KnowledgeIndexSnapshotEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private List<String> resolveEmbeddingCandidateModelIds(EvaluationDatasetEntity dataset, StartBenchmarkRunRequest request) {
        if (request.embeddingModelIds() != null && !request.embeddingModelIds().isEmpty()) {
            return request.embeddingModelIds();
        }
        if (!request.useWorkbookCandidatesEffective()) {
            return List.of();
        }
        EvaluationWorkbook wb = parseWorkbookForCandidates(dataset);
        List<String> out = new ArrayList<>();
        for (EmbeddingCandidate c : wb.embeddingCandidates()) {
            String model = c.model() != null && !c.model().isBlank() ? c.model().trim() : null;
            if (model != null && !model.isBlank()) {
                out.add(model);
            }
        }
        return out;
    }

    private void finalizeEmbeddingRetrievalRuntimeBinding(BenchmarkKind kind, EvaluationRunEntity run) {
        if (kind != BenchmarkKind.EMBEDDING_RETRIEVAL) {
            return;
        }
        KnowledgeIndexSnapshotEntity idx = run.getIndexSnapshot();
        Optional<String> prof =
                idx == null ? Optional.empty() : IndexProfileJsonSupport.readEmbeddingModelId(idx.getIndexProfileJsonb());
        boolean hasRunEmb = run.getEmbeddingModelId() != null && !run.getEmbeddingModelId().isBlank();
        if (idx == null && !hasRunEmb) {
            return;
        }
        String chosen;
        if (hasRunEmb) {
            chosen = run.getEmbeddingModelId().trim();
            if (prof.isPresent()
                    && !IndexProfileJsonSupport.normalizeEmbeddingKey(chosen)
                            .equals(IndexProfileJsonSupport.normalizeEmbeddingKey(prof.get()))) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EMBEDDING_MODEL_INDEX_MISMATCH: evaluation_run.embedding_model_id "
                                + chosen
                                + " does not match knowledge_index_snapshot profile "
                                + prof.get()
                                + " — bind the snapshot indexed with that model or reindex.");
            }
        } else {
            if (prof.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "EMBEDDING_MODEL_REQUIRED: specify embeddingModelId or bind an index snapshot whose profile contains embeddingModelId");
            }
            chosen = prof.get().trim();
            run.setEmbeddingModelId(chosen);
        }
        Map<String, Object> agg = new LinkedHashMap<>();
        if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
            agg.putAll(run.getAggregatesJson());
        }
        agg.put("embeddingModelId", chosen);
        try {
            int dims = embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(chosen);
            run.setEmbeddingDimensions(dims);
            agg.put("embeddingDimensions", dims);
            agg.put("embeddingCompatibilityStatus", "COMPATIBLE");
        } catch (ResponseStatusException ex) {
            if (!isEmbeddingDimensionMismatch(ex)) {
                throw ex;
            }
            run.setEmbeddingDimensions(null);
            agg.put("embeddingCompatibilityStatus", "INCOMPATIBLE");
            agg.put("embeddingCompatibilityReason", ex.getReason());
            agg.put("embeddingCompatibilityErrorCode", "EMBEDDING_DIMENSION_MISMATCH");
        }
        if (idx != null) {
            agg.put("indexSnapshotId", idx.getId().toString());
            if (idx.getIndexProfileHash() != null && !idx.getIndexProfileHash().isBlank()) {
                agg.put("indexProfileHash", idx.getIndexProfileHash());
            }
            if (idx.getSignatureHash() != null) {
                agg.put("indexSignatureHash", idx.getSignatureHash());
            }
        }
        run.setAggregatesJson(Map.copyOf(agg));
    }

    private static boolean isEmbeddingDimensionMismatch(ResponseStatusException ex) {
        return ex != null && ex.getReason() != null && ex.getReason().contains("EMBEDDING_DIMENSION_MISMATCH");
    }

    private static boolean wantsLlmCampaign(StartBenchmarkRunRequest request) {
        return (request.llmModelIds() != null && !request.llmModelIds().isEmpty()) || request.useWorkbookCandidatesEffective();
    }

    private static boolean wantsEmbeddingCampaign(StartBenchmarkRunRequest request) {
        return (request.embeddingModelIds() != null && !request.embeddingModelIds().isEmpty()) || request.useWorkbookCandidatesEffective();
    }

    private static boolean wantsRagPresetCampaign(StartBenchmarkRunRequest request) {
        return request.experimentalPresetCodes() != null && request.experimentalPresetCodes().size() >= 2;
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

    private static String childRunName(String baseName, BenchmarkKind kind, String axisValue) {
        String prefix = baseName != null && !baseName.isBlank() ? baseName.trim() : kind.name();
        return switch (kind) {
            case RAG_PRESET_END_TO_END -> prefix + " — preset " + axisValue;
            case EMBEDDING_RETRIEVAL -> prefix + " — embedding " + axisValue;
            default -> prefix + " — model " + axisValue;
        };
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
            // Packaged reference workbook is listed for all authenticated LAB users; only other system datasets stay ADMIN-only.
            if (ExperimentalDatasetType.REFERENCE_BUNDLE.name().equals(ds.getExperimentalKind())) {
                return ds;
            }
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
        if (!request.experimentalPresetCodes().isEmpty()
                || request.autoReindexEffective()
                || request.bootstrapCorpusFromClasspathDocsEffective()) {
            Map<String, Object> agg = new LinkedHashMap<>();
            if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
                agg.putAll(run.getAggregatesJson());
            }
            if (!request.experimentalPresetCodes().isEmpty()) {
                agg.put(AGG_KEY_REQUESTED_PRESET_CODES, request.experimentalPresetCodes());
            }
            if (request.autoReindexEffective()) {
                agg.put(AGG_KEY_AUTO_REINDEX_POLICY, LabAutoReindexPolicy.fromRequest(request).toMap());
                agg.put(AGG_KEY_AUTO_REINDEX_LOCK_ACQUIRED, Boolean.FALSE);
                agg.put(AGG_KEY_AUTO_REINDEX_MODE, "CONTROLLED_ACTIVE_SNAPSHOT_MUTATION");
                agg.put(AGG_KEY_AUTO_REINDEX_WARNING, "ACTIVE_SNAPSHOT_MUTATION_ACCEPTED");
            }
            if (request.bootstrapCorpusFromClasspathDocsEffective()) {
                Map<String, Object> corpusPolicy = new LinkedHashMap<>();
                corpusPolicy.put("enabled", true);
                corpusPolicy.put("classpathDocsLocation", request.classpathDocsLocationOrDefault());
                corpusPolicy.put("corpusScope", request.bootstrapCorpusScopeOrDefault());
                corpusPolicy.put("skipExisting", request.bootstrapSkipExistingEffective());
                corpusPolicy.put("failOnDocumentError", request.bootstrapFailOnDocumentErrorEffective());
                agg.put(AGG_KEY_CORPUS_BOOTSTRAP_POLICY, corpusPolicy);
            }
            run.setAggregatesJson(Map.copyOf(agg));
        }
        if (request.llmModelId() != null && !request.llmModelId().isBlank()) {
            run.setLlmModelId(request.llmModelId().trim());
        }
        if (request.embeddingModelId() != null && !request.embeddingModelId().isBlank()) {
            run.setEmbeddingModelId(request.embeddingModelId().trim());
        }
    }

    private static void validateClasspathCorpusBootstrapRequest(BenchmarkKind kind, StartBenchmarkRunRequest request) {
        if (request == null || !request.bootstrapCorpusFromClasspathDocsEffective()) {
            return;
        }
        if (kind != BenchmarkKind.RAG_PRESET_END_TO_END) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, LabCorpusBootstrapErrors.UNSUPPORTED_BENCHMARK_KIND);
        }
        if (request.corpusId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, EvaluationCorpusApplicationService.NO_CORPUS_SELECTED);
        }
    }

    private void validateAutoReindexRequest(BenchmarkKind kind, StartBenchmarkRunRequest request) {
        if (request == null || !request.autoReindexEffective()) {
            return;
        }
        if (kind != BenchmarkKind.RAG_PRESET_END_TO_END) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AUTO_REINDEX_UNSUPPORTED_FOR_BENCHMARK_KIND: autoReindex is only supported for RAG_PRESET_END_TO_END");
        }
        if (request.corpusId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    EvaluationCorpusApplicationService.NO_CORPUS_SELECTED + ": autoReindex requires corpusId");
        }
        if (!request.allowActiveSnapshotMutationEffective()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AUTO_REINDEX_REQUIRES_ACTIVE_SNAPSHOT_MUTATION: autoReindex requires allowActiveSnapshotMutation=true");
        }
    }

    private void validateDocumentBackedCorpus(UUID userId, BenchmarkKind kind, StartBenchmarkRunRequest request) {
        if (kind != BenchmarkKind.RAG_PRESET_END_TO_END && kind != BenchmarkKind.EMBEDDING_RETRIEVAL) {
            return;
        }
        if (request == null || request.corpusId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, EvaluationCorpusApplicationService.NO_CORPUS_SELECTED);
        }
        EvaluationCorpusReadinessDto readiness =
                evaluationCorpusReadinessService.getReadiness(userId, request.corpusId());
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs != null) {
            UUID snapshotId =
                    readiness.selectedSnapshotIds() != null && !readiness.selectedSnapshotIds().isEmpty()
                            ? readiness.selectedSnapshotIds().getFirst()
                            : null;
            obs.labCorpusPreflight(
                    request.corpusId(),
                    readiness.primaryBlocker(),
                    readiness.runnable(),
                    snapshotId);
        }
        if (!readiness.runnable()) {
            String blocker =
                    readiness.primaryBlocker() != null
                            ? readiness.primaryBlocker()
                            : LabCorpusReasonCodes.NO_READY_DOCUMENTS;
            String httpCode = httpReasonCodeForReadinessBlocker(blocker);
            HttpStatus status =
                    LabCorpusReasonCodes.NO_READY_DOCUMENTS.equals(blocker)
                            ? HttpStatus.CONFLICT
                            : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status, httpCode);
        }
    }

    /** Maps readiness {@code NO_DOCUMENTS} to HTTP contract code {@code KB_EMPTY} (B7). */
    private static String httpReasonCodeForReadinessBlocker(String readinessBlocker) {
        if (LabCorpusReasonCodes.NO_DOCUMENTS.equals(readinessBlocker)) {
            return LabCorpusReasonCodes.KB_EMPTY;
        }
        return readinessBlocker;
    }

    private void applyCorpusReadinessAggregates(
            UUID userId, EvaluationRunEntity run, StartBenchmarkRunRequest request) {
        if (request == null || request.corpusId() == null) {
            return;
        }
        if (run.getBenchmarkKind() != null
                && !BenchmarkKind.RAG_PRESET_END_TO_END.name().equals(run.getBenchmarkKind())
                && !BenchmarkKind.EMBEDDING_RETRIEVAL.name().equals(run.getBenchmarkKind())) {
            return;
        }
        EvaluationCorpusReadinessDto readiness =
                evaluationCorpusReadinessService.getReadiness(userId, request.corpusId());
        Map<String, Object> agg = new LinkedHashMap<>();
        if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
            agg.putAll(run.getAggregatesJson());
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("corpusId", request.corpusId().toString());
        snapshot.put("documentCount", readiness.documentCount());
        snapshot.put("readyCount", readiness.readyCount());
        snapshot.put("processingCount", readiness.processingCount());
        snapshot.put("failedCount", readiness.failedCount());
        snapshot.put("primaryBlocker", readiness.primaryBlocker());
        snapshot.put("snapshotBlocker", readiness.snapshotBlocker());
        snapshot.put("snapshotBlockerDetailCode", readiness.snapshotBlockerDetailCode());
        snapshot.put("reindexRequired", readiness.reindexRequired());
        snapshot.put(
                "selectedSnapshotIds",
                readiness.selectedSnapshotIds() != null
                        ? readiness.selectedSnapshotIds().stream().map(UUID::toString).toList()
                        : List.of());
        agg.put(AGG_KEY_CORPUS_READINESS, Collections.unmodifiableMap(snapshot));
        run.setAggregatesJson(new LinkedHashMap<>(agg));
    }

    private void applyConfigPreflightAggregates(EvaluationRunEntity run, LabBenchmarkConfigPreflightResult preflight) {
        if (run == null || preflight == null) {
            return;
        }
        Map<String, Object> agg = new LinkedHashMap<>();
        if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
            agg.putAll(run.getAggregatesJson());
        }
        agg.put(AGG_KEY_CONFIG_PREFLIGHT, preflight.toAggregatesMap());
        run.setAggregatesJson(Map.copyOf(agg));
    }

    private void applyCorpusLinks(UUID userId, EvaluationRunEntity run, StartBenchmarkRunRequest request) {
        if (request == null || request.corpusId() == null) {
            return;
        }
        EvaluationCorpusEntity corpus =
                evaluationCorpusRepository
                        .findByIdAndOwner_Id(request.corpusId(), userId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                EvaluationCorpusApplicationService.KB_NOT_FOUND));
        run.setEvaluationCorpus(corpus);
        if (corpus.getIndexProject() != null) {
            run.setProject(corpus.getIndexProject());
        }
    }

    private UUID resolveConcurrencyScopeId(UUID userId, StartBenchmarkRunRequest request) {
        if (request == null) {
            return null;
        }
        if (request.corpusId() != null) {
            EvaluationCorpusApplicationService.EvaluationCorpusContext context =
                    evaluationCorpusApplicationService.requireContext(userId, request.corpusId());
            return context.indexProjectId();
        }
        return request.projectId();
    }

    private void attachTaskAndRunning(EvaluationRunEntity run, UUID taskId) {
        AsyncTaskEntity task = asyncTaskRepository.findById(taskId).orElseThrow();
        run.setAsyncTask(task);
        run.setStatus(EvaluationRunStatus.RUNNING);
        evaluationRunRepository.save(run);
    }

    private void requireNoActiveLabJob(UUID userId, UUID projectIdOrNull) {
        ActiveLabJobDto active = labJobLifecycleService.findFirstActiveJobForScope(userId, projectIdOrNull);
        if (active == null || active.jobId() == null) {
            return;
        }
        log.info(
                "lab_job_rejected_concurrent userId={} projectId={} activeJobId={} activeBenchmarkKind={}",
                userId,
                projectIdOrNull,
                active.jobId(),
                active.benchmarkKind());
        throw new LabJobConcurrencyException(
                "Another Lab evaluation is already running. Cancel it or wait for it to finish.",
                active);
    }

    private static EvaluationRunType mapRunType(BenchmarkKind kind) {
        return switch (kind) {
            case LLM_JUDGE_QA -> EvaluationRunType.LLM_ONLY;
            case RAG_PRESET_END_TO_END -> EvaluationRunType.RAG_FULL;
            case EMBEDDING_RETRIEVAL -> EvaluationRunType.RAG_FULL;
            case CLASSIFIER_METRICS -> EvaluationRunType.CLASSIFIER;
        };
    }

    private static int resolveDatasetItemCount(EvaluationDatasetEntity dataset, BenchmarkKind kind) {
        Integer stored = dataset.getQuestionCount();
        if (stored != null && stored > 0) {
            return stored;
        }
        return 0;
    }
}
