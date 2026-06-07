package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminModelUpsertRequest(
        @NotBlank @Size(max = 255) String modelId,
        @Size(max = 255) String displayName,
        @NotNull AllowedModelType modelType,
        boolean enabled,
        boolean pullIfMissing,
        List<String> tags) {}

