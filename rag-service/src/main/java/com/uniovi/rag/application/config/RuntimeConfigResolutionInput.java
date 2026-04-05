package com.uniovi.rag.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single canonical input for {@link ConfigResolverService}. At most one of preset payload and standalone
 * runtime override should carry preset JSON; {@link #effectiveRuntimeOverride()} defines precedence.
 */
public record RuntimeConfigResolutionInput(
        UUID userId,
        UUID projectId,
        Optional<String> conversationId,
        Optional<String> presetId,
        Optional<JsonNode> presetPayload,
        Optional<JsonNode> runtimeOverride,
        Set<ConfigProfileType> touchedProfileTypes,
        Optional<CapabilitySet> baselineCapabilitySet,
        Optional<ResolvedRuntimeConfig> baselineResolved,
        Optional<String> correlationId) {

    public RuntimeConfigResolutionInput {
        touchedProfileTypes = touchedProfileTypes == null ? Set.of() : Set.copyOf(touchedProfileTypes);
    }

    /**
     * Precedence: explicit {@code runtimeOverride} if present, otherwise {@code presetPayload}.
     */
    public JsonNode effectiveRuntimeOverride() {
        if (runtimeOverride.isPresent()) {
            return runtimeOverride.get();
        }
        return presetPayload.orElse(null);
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
