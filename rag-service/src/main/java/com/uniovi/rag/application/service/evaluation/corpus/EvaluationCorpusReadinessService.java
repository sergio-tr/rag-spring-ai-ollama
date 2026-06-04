package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.application.service.evaluation.RagBenchmarkHumanReasons;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationCorpusReadinessService {

    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final CorpusAvailabilityGate corpusAvailabilityGate;
    private final KnowledgeSnapshotService knowledgeSnapshotService;

    public EvaluationCorpusReadinessService(
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            CorpusAvailabilityGate corpusAvailabilityGate,
            KnowledgeSnapshotService knowledgeSnapshotService) {
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.corpusAvailabilityGate = corpusAvailabilityGate;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
    }

    @Transactional(readOnly = true)
    public EvaluationCorpusReadinessDto getReadiness(UUID userId, UUID corpusId) {
        EvaluationCorpusApplicationService.EvaluationCorpusContext context =
                evaluationCorpusApplicationService.requireContext(userId, corpusId);
        List<KnowledgeDocumentEntity> docs =
                context.documents() != null ? context.documents() : List.of();
        int ready = 0;
        int processing = 0;
        int failed = 0;
        for (KnowledgeDocumentEntity doc : docs) {
            if (doc == null) {
                continue;
            }
            if (doc.getStatus() == ProjectDocumentStatus.READY) {
                ready++;
            } else if (doc.getStatus() == ProjectDocumentStatus.INGESTING) {
                processing++;
            } else if (doc.getStatus() == ProjectDocumentStatus.ERROR) {
                failed++;
            }
        }

        String primaryBlocker = null;
        if (docs.isEmpty()) {
            primaryBlocker = LabCorpusReasonCodes.NO_DOCUMENTS;
        } else if (ready <= 0) {
            if (processing > 0) {
                primaryBlocker = LabCorpusReasonCodes.NO_READY_DOCUMENTS;
            } else if (failed > 0) {
                primaryBlocker = LabCorpusReasonCodes.DOCUMENT_PROCESSING_FAILED;
            } else {
                primaryBlocker = LabCorpusReasonCodes.NO_READY_DOCUMENTS;
            }
        }

        Optional<KnowledgeIndexSnapshotEntity> activeSnapshot =
                knowledgeSnapshotService.findActiveCorpusSnapshot(corpusId);
        UUID activeSnapshotId = activeSnapshot.map(KnowledgeIndexSnapshotEntity::getId).orElse(null);
        List<UUID> snapshotIds = new ArrayList<>();
        if (activeSnapshotId != null) {
            snapshotIds.add(activeSnapshotId);
        }

        String snapshotBlocker = null;
        String snapshotBlockerDetailCode = null;
        boolean reindexRequired = false;
        if (primaryBlocker == null) {
            if (activeSnapshotId == null) {
                snapshotBlocker = LabCorpusReasonCodes.REINDEX_REQUIRED;
                snapshotBlockerDetailCode = "NO_ACTIVE_INDEX";
                reindexRequired = true;
            } else {
                CorpusAvailabilityGate.Result gate =
                        corpusAvailabilityGate.evaluate(userId, corpusId, snapshotIds);
                if (!gate.satisfied()) {
                    snapshotBlocker = normalizeSnapshotBlocker(gate.reasonCode());
                    reindexRequired =
                            LabCorpusReasonCodes.REINDEX_REQUIRED.equals(snapshotBlocker)
                                    || LabCorpusReasonCodes.NO_ACTIVE_SNAPSHOT.equals(snapshotBlocker)
                                    || LabCorpusReasonCodes.SNAPSHOT_VECTOR_ROWS_MISSING.equals(snapshotBlocker)
                                    || LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT.equals(snapshotBlocker);
                }
            }
        }

        String message =
                primaryBlocker != null
                        ? RagBenchmarkHumanReasons.humanize(primaryBlocker)
                        : (snapshotBlocker != null ? RagBenchmarkHumanReasons.humanize(snapshotBlocker) : null);
        boolean runnable = ready > 0 && primaryBlocker == null;

        return new EvaluationCorpusReadinessDto(
                corpusId,
                context.indexProjectId(),
                docs.size(),
                ready,
                processing,
                failed,
                primaryBlocker,
                message,
                activeSnapshotId,
                reindexRequired,
                snapshotBlocker,
                snapshotBlockerDetailCode,
                List.copyOf(snapshotIds),
                runnable);
    }

    private static String normalizeSnapshotBlocker(String gateCode) {
        if (gateCode == null || gateCode.isBlank()) {
            return LabCorpusReasonCodes.REINDEX_REQUIRED;
        }
        if (CorpusAvailabilityGate.REINDEX_REQUIRED.equals(gateCode)) {
            return LabCorpusReasonCodes.REINDEX_REQUIRED;
        }
        if (CorpusAvailabilityGate.SNAPSHOT_VECTOR_ROWS_MISSING.equals(gateCode)) {
            return LabCorpusReasonCodes.SNAPSHOT_VECTOR_ROWS_MISSING;
        }
        if (CorpusAvailabilityGate.NO_COMPATIBLE_SNAPSHOT.equals(gateCode)) {
            return LabCorpusReasonCodes.NO_COMPATIBLE_SNAPSHOT;
        }
        return gateCode;
    }
}
