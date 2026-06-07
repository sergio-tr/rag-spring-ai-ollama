package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminModelUpdateRequest(
        @Size(max = 255) String displayName,
        AllowedModelType modelType,
        Boolean enabled,
        List<String> tags) {}
