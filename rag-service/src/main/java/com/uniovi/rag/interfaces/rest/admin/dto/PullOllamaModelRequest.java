package com.uniovi.rag.interfaces.rest.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PullOllamaModelRequest(@NotBlank @Size(max = 512) String model) {}
