package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminModelCheckRequest(
        @NotBlank @Size(max = 255) String modelId,
        @NotNull AllowedModelType modelType,
        /** When true, attempt to pull the model if missing before final validation. */
        boolean pullIfMissing) {}

