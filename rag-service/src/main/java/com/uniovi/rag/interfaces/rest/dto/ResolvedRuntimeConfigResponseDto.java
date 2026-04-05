package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.config.indexing.ReindexPreview;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;

import java.util.List;
import java.util.Map;

public record ResolvedRuntimeConfigResponseDto(
        Map<String, Object> resolvedCore,
        CapabilitySetDto capabilitySet,
        CompatibilityResultDto compatibility,
        PromptStackDto promptStack,
        ReindexPreviewDto reindexPreview,
        Map<String, Object> legacyProjection) {

    public static ResolvedRuntimeConfigResponseDto fromDomain(ResolvedRuntimeConfig r) {
        ReindexPreview idx = r.reindexPreview();
        if (idx == null) {
            idx = new ReindexPreview(false, List.of());
        }
        return new ResolvedRuntimeConfigResponseDto(
                r.resolvedCoreConfig().toValueMap(),
                CapabilitySetDto.fromDomain(r.capabilitySet()),
                CompatibilityResultDto.fromDomain(r.compatibility()),
                PromptStackDto.fromDomain(r.promptStack()),
                ReindexPreviewDto.fromDomain(idx),
                r.legacyProjection() != null ? r.legacyProjection().toValueMap() : r.resolvedCoreConfig().toValueMap());
    }
}
