package com.uniovi.rag.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backward-compatible facade delegating to {@link ConfigResolverService} with {@link RuntimeConfigResolutionInput}.
 */
@Service
public class RuntimeConfigResolutionService {

    private final ConfigResolverService configResolverService;

    public RuntimeConfigResolutionService(ConfigResolverService configResolverService) {
        this.configResolverService = configResolverService;
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig resolve(UUID userId, UUID projectId, JsonNode runtimeOverride) {
        return configResolverService.resolve(
                RuntimeConfigResolutionInput.forResolve(userId, projectId, runtimeOverride));
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig preview(RuntimeConfigResolutionInput input) {
        return configResolverService.preview(input);
    }

    /**
     * Single live resolution path for the runtime engine (merged conversation JSON as terminal override when present).
     */
    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig resolveForOrchestratedExecute(
            UUID userId, UUID projectId, JsonNode terminalConversationMergedOverride, String correlationId) {
        return configResolverService.resolve(
                RuntimeConfigResolutionInput.forOrchestratedResolve(
                        userId, projectId, terminalConversationMergedOverride, correlationId));
    }

    @Transactional(readOnly = true)
    public ResolvedRuntimeConfig preview(
            UUID userId,
            UUID projectId,
            JsonNode runtimeOverride,
            Set<ConfigProfileType> touchedProfileTypes,
            CapabilitySet baselineCapability) {
        Set<ConfigProfileType> touched =
                touchedProfileTypes == null ? Set.of() : Set.copyOf(touchedProfileTypes);
        // Delegate to resolver (same as {@link #preview(RuntimeConfigResolutionInput)}) — avoids internal overload call.
        return configResolverService.preview(
                new RuntimeConfigResolutionInput(
                        userId,
                        projectId,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.ofNullable(runtimeOverride),
                        touched,
                        Optional.ofNullable(baselineCapability),
                        Optional.empty(),
                        Optional.empty()));
    }
}
