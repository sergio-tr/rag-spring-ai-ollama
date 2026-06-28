package com.uniovi.rag.interfaces.rest.dto.modelregistry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelRegistryCheckRequest(
        @NotBlank @Size(max = 255) String modelId,
        /**
         * When true (default) and the resolved row is an embedding model, runs a lightweight embed probe against Ollama.
         */
        Boolean probeEmbedding) {}
