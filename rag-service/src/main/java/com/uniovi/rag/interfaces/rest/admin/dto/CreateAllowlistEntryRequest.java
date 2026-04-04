package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAllowlistEntryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull AllowedModelType type,
        boolean inAllowlist) {}
