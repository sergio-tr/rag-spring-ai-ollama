package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.util.Map;

public record ResolvedRuntimeConfigResponseDto(
        Map<String, Object> resolvedCore,
        CapabilitySetDto capabilitySet,
        CompatibilityResultDto compatibility,
        SystemPromptLayersDto systemPromptLayers,
        String effectiveSystemPrompt,
        ReindexImpactDto reindexImpact,
        Map<String, Object> configProjection) {

    public static ResolvedRuntimeConfigResponseDto fromDomain(ResolvedRuntimeConfig r) {
        return new ResolvedRuntimeConfigResponseDto(
                r.resolvedCoreConfig().toValueMap(),
                CapabilitySetDto.fromDomain(r.capabilitySet()),
                CompatibilityResultDto.fromDomain(r.compatibility()),
                SystemPromptLayersDto.fromDomain(r.systemPromptLayers()),
                r.effectiveSystemPrompt() != null ? r.effectiveSystemPrompt() : "",
                ReindexImpactDto.fromDomain(r.reindexImpact()),
                r.configProjection() != null ? r.configProjection().toValueMap() : r.resolvedCoreConfig().toValueMap());
    }
}
