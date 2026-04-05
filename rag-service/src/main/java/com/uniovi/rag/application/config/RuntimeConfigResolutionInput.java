package com.uniovi.rag.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single canonical input for {@link ConfigResolverService}. When {@code presetId} is empty, {@code presetPayload}
 * may still supply terminal JSON (same role as legacy preview). When {@code presetId} is present, preset/profile
 * layers load from persistence; terminal JSON is only {@code runtimeOverride}.
 */
public record RuntimeConfigResolutionInput(
        UUID userId,
        UUID projectId,
        Optional<UUID> conversationId,
        Optional<UUID> presetId,
        Optional<JsonNode> presetPayload,
        Optional<JsonNode> runtimeOverride,
        Set<ConfigProfileType> touchedProfileTypes,
        Optional<CapabilitySet> baselineCapabilitySet,
        Optional<ResolvedRuntimeConfig> baselineResolved,
        Optional<String> correlationId) {

    public RuntimeConfigResolutionInput {
        touchedProfileTypes = touchedProfileTypes == null ? Set.of() : Set.copyOf(touchedProfileTypes);
    }

    public static RuntimeConfigResolutionInput forResolve(UUID userId, UUID projectId, JsonNode runtimeOverride) {
        return new RuntimeConfigResolutionInput(
                userId,
                projectId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(runtimeOverride),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
