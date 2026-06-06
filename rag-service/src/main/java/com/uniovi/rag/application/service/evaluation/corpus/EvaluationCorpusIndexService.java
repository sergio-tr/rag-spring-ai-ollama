package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Builds evaluation-corpus-scoped index snapshots for Lab benchmarks. */
@Service
public class EvaluationCorpusIndexService {

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    public EvaluationCorpusIndexService(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
    }

    @Transactional
    public void prepareIndex(UUID userId, UUID corpusId) {
        EvaluationCorpusApplicationService.EvaluationCorpusContext context =
                evaluationCorpusApplicationService.requireReadyContext(userId, corpusId);
        evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId);
        UUID indexProjectId = context.indexProjectId();
        if (indexProjectId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.NO_READY_DOCUMENTS);
        }
        ProjectIndexProfile profile = projectIndexProfileService.ensureDefault(indexProjectId);
        ResolvedConfigSnapshotLinkage resolved =
                resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        userId, indexProjectId, Optional.empty());
        UUID snapshotId;
        try {
            snapshotId =
                    knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                            indexProjectId,
                            CorpusScope.PROJECT_SHARED,
                            null,
                            KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                            corpusId,
                            resolved.id(),
                            resolved.configHash(),
                            profile);
        } catch (IllegalArgumentException e) {
            if (isResolvedConfigLinkageFailure(e)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE,
                        e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.REINDEX_REQUIRED, e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.REINDEX_REQUIRED, e);
        }
        if (snapshotId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.NO_READY_DOCUMENTS);
        }
    }

    private static boolean isResolvedConfigLinkageFailure(Throwable e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("resolved_config_snapshot");
    }
}
