package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.service.evaluation.mvp.BenchmarkMvpMetricsCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LabCampaignService {

    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;

    public LabCampaignService(
            EvaluationCampaignRepository evaluationCampaignRepository,
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository) {
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", c.getId());
        out.put("studyType", c.getStudyType());
        out.put("name", c.getName());
        out.put("createdAt", c.getCreatedAt());
        out.put("projectId", c.getProject() != null ? c.getProject().getId() : null);
        out.put("runCount", runs.size());
        out.put("runIds", runs.stream().map(EvaluationRunEntity::getId).toList());
        out.put("meta", c.getMetaJson() != null ? c.getMetaJson() : Map.of());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(UUID userId, UUID campaignId) {
        requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvaluationRunEntity r : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", r.getId());
            row.put("name", r.getName());
            row.put("benchmarkKind", r.getBenchmarkKind());
            row.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            row.put("createdAt", r.getCreatedAt());
            row.put("completedAt", r.getCompletedAt());
            row.put("llmModelId", r.getLlmModelId());
            row.put("embeddingModelId", r.getEmbeddingModelId());
            out.add(row);
        }
        return out;
    }

    /**
     * Campaign MVP items (JSON) — concatenates all child-run items and preserves modelId/embeddingModelId per row.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignMvpItemsJson(UUID userId, UUID campaignId) {
        requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> rows = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            for (EvaluationResultEntity it : rows) {
                Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(it, run);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("campaignId", campaignId);
                row.put("runId", run.getId());
                row.put("evaluatedAt", it.getEvaluatedAt());
                row.put("mvp", mvp);
                items.add(row);
            }
        }
        return Map.of(
                "campaignId", campaignId,
                "exportedAt", Instant.now().toString(),
                "items", items);
    }

    private EvaluationCampaignEntity requireCampaign(UUID userId, UUID campaignId) {
        return evaluationCampaignRepository
                .findByIdAndUser_Id(campaignId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }
}

