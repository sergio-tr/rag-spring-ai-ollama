package com.uniovi.rag.interfaces.rest.dto.modelregistry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelRegistryPullRequest(@NotBlank @Size(max = 255) String modelId) {}
