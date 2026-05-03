package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateConfigProfileRequest(
        @NotBlank String profileType,
        int version,
        String label,
        @NotNull Map<String, Object> payload,
        /** When true, profile is system-scoped (owner null); requires admin. */
        boolean systemScope) {
}
