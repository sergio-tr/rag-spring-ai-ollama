package com.uniovi.rag.application.service.evaluation.preset;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Campaign-scoped parent preset outcomes that survive per-preset benchmark boundaries. */
final class CampaignParentOutcomeStore {

    private static final ConcurrentHashMap<UUID, Map<String, Map<String, CampaignParentOutcome>>> BY_CAMPAIGN =
            new ConcurrentHashMap<>();

    private CampaignParentOutcomeStore() {}

    static void record(UUID campaignId, String presetCode, String datasetQuestionId, CampaignParentOutcome outcome) {
        if (campaignId == null
                || presetCode == null
                || presetCode.isBlank()
                || datasetQuestionId == null
                || datasetQuestionId.isBlank()
                || outcome == null) {
            return;
        }
        BY_CAMPAIGN
                .computeIfAbsent(campaignId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(presetCode, ignored -> new ConcurrentHashMap<>())
                .put(datasetQuestionId, outcome);
    }

    static Optional<CampaignParentOutcome> lookup(UUID campaignId, String presetCode, String datasetQuestionId) {
        if (campaignId == null
                || presetCode == null
                || presetCode.isBlank()
                || datasetQuestionId == null
                || datasetQuestionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CAMPAIGN.get(campaignId))
                .map(byPreset -> byPreset.get(presetCode))
                .map(byQuestion -> byQuestion.get(datasetQuestionId));
    }

    static boolean has(UUID campaignId, String presetCode, String datasetQuestionId) {
        return lookup(campaignId, presetCode, datasetQuestionId).isPresent();
    }

    static void clear(UUID campaignId) {
        if (campaignId != null) {
            BY_CAMPAIGN.remove(campaignId);
        }
    }

    /** Test-only visibility of stored preset keys for a campaign. */
    static Map<String, Map<String, CampaignParentOutcome>> snapshot(UUID campaignId) {
        Map<String, Map<String, CampaignParentOutcome>> stored = BY_CAMPAIGN.get(campaignId);
        if (stored == null) {
            return Map.of();
        }
        Map<String, Map<String, CampaignParentOutcome>> copy = new HashMap<>();
        stored.forEach(
                (preset, byQuestion) -> copy.put(preset, Map.copyOf(byQuestion)));
        return Map.copyOf(copy);
    }
}
