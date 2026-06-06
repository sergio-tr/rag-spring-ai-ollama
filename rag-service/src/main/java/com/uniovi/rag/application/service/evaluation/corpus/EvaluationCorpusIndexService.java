package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
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

    public EvaluationCorpusIndexService(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
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
        UUID snapshotId =
                knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        indexProjectId,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                        corpusId,
                        null,
                        null,
                        profile);
        if (snapshotId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.NO_READY_DOCUMENTS);
        }
    }
}
