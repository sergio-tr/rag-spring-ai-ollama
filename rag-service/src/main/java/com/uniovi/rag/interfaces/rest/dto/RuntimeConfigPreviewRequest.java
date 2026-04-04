package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuntimeConfigPreviewRequest(
        @NotNull UUID projectId,
        Map<String, Object> runtimeOverride,
        List<String> touchedProfileTypes,
        CapabilitySetDto baselineCapabilitySnapshot) {
}
