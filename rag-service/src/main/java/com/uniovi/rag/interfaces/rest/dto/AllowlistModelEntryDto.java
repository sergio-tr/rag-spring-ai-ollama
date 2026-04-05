package com.uniovi.rag.interfaces.rest.dto;

import com.uniovi.rag.domain.AllowedModelType;

/**
 * One row from {@code allowed_model} with a flag indicating Ollama currently lists that name.
 */
public record AllowlistModelEntryDto(
        String name, AllowedModelType type, boolean inAllowlist, boolean installedInOllama) {}
