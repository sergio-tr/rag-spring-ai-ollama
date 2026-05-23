package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import java.time.Instant;
import java.util.List;

public record AdminModelCheckResponse(
        String modelId,
        AllowedModelType requestedType,
        boolean existsLocal,
        boolean canPull,
        boolean pulled,
        boolean embeddingProbeOk,
        List<String> matchedLocalIds,
        Instant checkedAt,
        String errorCode,
        String errorMessage,
        /** Operator detail (HTTP body snippet, endpoint); not for default UI copy. */
        String technicalDetail,
        String pullSummary) {}

