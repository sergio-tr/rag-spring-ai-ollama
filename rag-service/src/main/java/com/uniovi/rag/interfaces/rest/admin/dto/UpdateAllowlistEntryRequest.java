package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;
import jakarta.validation.constraints.Size;

public record UpdateAllowlistEntryRequest(
        @Size(max = 255) String name, AllowedModelType type, Boolean inAllowlist) {}
