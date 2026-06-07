package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import java.util.UUID;

public record AdminModelDeleteResponse(
        UUID id,
        String modelId,
        AllowedModelType modelType,
        /** {@code DELETED} when removed; {@code DISABLED} when soft-disabled due to historical references. */
        String outcome,
        String message) {}
