package com.uniovi.rag.interfaces.rest.dto;

public record DisabledRuntimeFeatureDto(
        String key,
        String reasonCode,
        String reason
) {}
