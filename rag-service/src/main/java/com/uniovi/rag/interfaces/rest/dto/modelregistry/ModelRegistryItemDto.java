package com.uniovi.rag.interfaces.rest.dto.modelregistry;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;

/**
 * One curated model: type split at the response envelope level; this record repeats {@code modelType} for convenience.
 */
public record ModelRegistryItemDto(
        String modelId,
        AllowedModelType modelType,
        ModelRegistryAvailabilityStatus status,
        /** Ollama or probe error text; null when healthy. */
        String detail,
        /**
         * When {@link #modelType()} is embedding and a probe ran: whether the model answered an embed call.
         * Null when not probed (list snapshot) or for LLM rows.
         */
        Boolean embeddingCompatible) {}
