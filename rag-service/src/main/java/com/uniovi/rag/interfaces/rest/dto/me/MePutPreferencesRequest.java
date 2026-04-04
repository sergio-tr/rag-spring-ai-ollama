package com.uniovi.rag.interfaces.rest.dto.me;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MePutPreferencesRequest(Integer schemaVersion, @NotNull Map<String, Object> preferences) {}
