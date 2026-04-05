package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PresetProfileRefDto(@Min(0) int ordinal, @NotNull UUID profileId, String role) {}
