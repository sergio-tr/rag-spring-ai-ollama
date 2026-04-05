package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

/**
 * Allowlisted models from the database plus names reported by {@code GET /api/tags} on Ollama.
 */
public record ModelsCatalogResponseDto(
        boolean ollamaReachable,
        List<String> installedModelNames,
        List<AllowlistModelEntryDto> allowlist) {}
