package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabCampaignServiceTest {

    @Test
    void exportCampaignMvpItemsJson_containsCampaignAndRunIds() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = new EvaluationCampaignEntity();
        c.setId(campaignId);
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(userId);
        c.setUser(u);
        c.setStudyType("LLM_MODEL_BASELINE");
        c.setCreatedAt(Instant.now());
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r = new EvaluationRunEntity();
        UUID runId = UUID.randomUUID();
        r.setId(runId);
        r.setLlmModelId("m1");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r));

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setBenchmarkKind("LLM_JUDGE_QA");
        item.setEvaluatedAt(Instant.now());
        item.setMetricsPayload(Map.of());
        when(results.findByRun_IdOrderByEvaluatedAtAsc(runId)).thenReturn(List.of(item));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.exportCampaignMvpItemsJson(userId, campaignId);
        assertThat(out.get("campaignId")).isEqualTo(campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("items");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("campaignId")).isEqualTo(campaignId);
        assertThat(rows.getFirst().get("runId")).isEqualTo(runId);
    }

    @Test
    void campaignComparison_rollsUpExecutedAndNotSupported() {
        UUID userId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        EvaluationCampaignRepository campaigns = mock(EvaluationCampaignRepository.class);
        EvaluationRunRepository runs = mock(EvaluationRunRepository.class);
        EvaluationResultRepository results = mock(EvaluationResultRepository.class);

        EvaluationCampaignEntity c = new EvaluationCampaignEntity();
        c.setId(campaignId);
        UserEntity u = mock(UserEntity.class);
        when(u.getId()).thenReturn(userId);
        c.setUser(u);
        c.setStudyType("LLM_MODEL_BASELINE");
        c.setCreatedAt(Instant.now());
        when(campaigns.findByIdAndUser_Id(campaignId, userId)).thenReturn(Optional.of(c));

        EvaluationRunEntity r = new EvaluationRunEntity();
        UUID runId = UUID.randomUUID();
        r.setId(runId);
        r.setLlmModelId("m1");
        r.setBenchmarkKind("LLM_JUDGE_QA");
        when(runs.findByCampaignIdAndUserId(campaignId, userId)).thenReturn(List.of(r));

        EvaluationResultEntity item1 = new EvaluationResultEntity();
        item1.setId(UUID.randomUUID());
        item1.setBenchmarkKind("LLM_JUDGE_QA");
        item1.setEvaluatedAt(Instant.now());
        item1.setMetricsPayload(Map.of("item_outcome", "EXECUTED"));

        EvaluationResultEntity item2 = new EvaluationResultEntity();
        item2.setId(UUID.randomUUID());
        item2.setBenchmarkKind("LLM_JUDGE_QA");
        item2.setEvaluatedAt(Instant.now());
        item2.setMetricsPayload(Map.of("item_outcome", "NOT_SUPPORTED", "error_code", "SOME_REASON"));

        when(results.findByRun_IdOrderByEvaluatedAtAsc(runId)).thenReturn(List.of(item1, item2));

        LabCampaignService svc = new LabCampaignService(campaigns, runs, results);
        Map<String, Object> out = svc.campaignComparison(userId, campaignId);
        assertThat(out.get("campaignId")).isEqualTo(campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("groupKey", "modelId").containsEntry("groupValue", "m1");
        assertThat(((Number) rows.getFirst().get("totalItems")).longValue()).isEqualTo(2L);
        assertThat(((Number) rows.getFirst().get("executed")).longValue()).isEqualTo(1L);
        assertThat(((Number) rows.getFirst().get("notSupported")).longValue()).isEqualTo(1L);
    }
}

