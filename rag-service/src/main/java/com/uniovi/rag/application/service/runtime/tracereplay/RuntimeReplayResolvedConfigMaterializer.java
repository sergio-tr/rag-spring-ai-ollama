package com.uniovi.rag.application.service.runtime.tracereplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reconstructs {@link ResolvedRuntimeConfig} from a persisted {@link ResolvedConfigSnapshotEntity} row (P18 replay pin).
 * Does not re-resolve live configuration.
 */
@Component
public class RuntimeReplayResolvedConfigMaterializer {

    private final ObjectMapper objectMapper;

    public RuntimeReplayResolvedConfigMaterializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @return empty when the snapshot row cannot be materialized into a usable {@link ResolvedRuntimeConfig}.
     */
    public Optional<ResolvedRuntimeConfig> materialize(ResolvedConfigSnapshotEntity entity) {
        if (entity == null || entity.getPayloadJsonb() == null || entity.getPayloadJsonb().isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>(entity.getPayloadJsonb());
            payload.remove(KnowledgeBuildProjectionMapper.PAYLOAD_KEY);
            RagConfig core = objectMapper.convertValue(payload, RagConfig.class);
            CapabilitySet caps = CapabilitySet.fromRagConfig(core);
            CompatibilityResult compatibility =
                    read(entity.getCompatibilityResultJsonb(), CompatibilityResult.class)
                            .orElse(CompatibilityResult.ok());
            ReindexImpact impact =
                    read(entity.getReindexImpactJsonb(), ReindexImpact.class).orElse(ReindexImpact.none());
            SystemPromptLayers layers =
                    read(entity.getSystemPromptLayersJsonb(), SystemPromptLayers.class)
                            .orElse(SystemPromptLayers.empty());
            String eff =
                    entity.getEffectiveSystemPrompt() != null ? entity.getEffectiveSystemPrompt() : "";
            return Optional.of(
                    new ResolvedRuntimeConfig(
                            core,
                            caps,
                            compatibility,
                            impact,
                            layers,
                            eff,
                            new ConfigProvenance(null, null, null, List.of(), null, entity.getId()),
                            core));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> read(Map<String, Object> jsonb, Class<T> type) {
        if (jsonb == null || jsonb.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.convertValue(jsonb, type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
