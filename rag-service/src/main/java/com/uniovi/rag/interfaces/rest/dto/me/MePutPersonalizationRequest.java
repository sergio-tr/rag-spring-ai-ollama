package com.uniovi.rag.interfaces.rest.dto.me;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MePutPersonalizationRequest(Integer schemaVersion, @NotNull Map<String, Object> personalization) {}
