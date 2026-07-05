package com.uniovi.rag.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.application.config.RuntimeConfigResolutionInput;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    public RuntimeConfigResolutionService(
            ConfigResolverService configResolverService, ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.configResolverService = configResolverService;
        this.runtimeObservability = runtimeObservability;
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
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs == null) {
            return configResolverService.resolve(
                    RuntimeConfigResolutionInput.forOrchestratedResolve(
                            userId, projectId, terminalConversationMergedOverride, correlationId));
        }
        ResolvedRuntimeConfig resolved =
                obs.configResolve(
                        userId,
                        projectId,
                        null,
                        0,
                        () ->
                                configResolverService.resolve(
                                        RuntimeConfigResolutionInput.forOrchestratedResolve(
                                                userId, projectId, terminalConversationMergedOverride, correlationId)));
        if (resolved != null) {
            Map<String, String> enrich = new LinkedHashMap<>();
            if (resolved.provenance() != null && resolved.provenance().presetId() != null) {
                enrich.put("presetId", resolved.provenance().presetId().toString());
            }
            if (resolved.provenance() != null && resolved.provenance().snapshotId() != null) {
                enrich.put("snapshotId", resolved.provenance().snapshotId().toString());
            }
            if (resolved.compatibility() != null) {
                enrich.put(
                        "blockingIssueCount",
                        String.valueOf(
                                resolved.compatibility().errors() != null
                                        ? resolved.compatibility().errors().size()
                                        : 0));
            }
            obs.enrichCurrentSpan(enrich);
        }
        return resolved;
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
        // Delegate to resolver (same as {@link #preview(RuntimeConfigResolutionInput)}) - avoids internal overload call.
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
