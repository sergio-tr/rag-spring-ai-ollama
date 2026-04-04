package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.domain.AllowedModelType;

import java.time.Instant;
import java.util.UUID;

public record AdminAllowlistEntryDto(
        UUID id, String name, AllowedModelType type, boolean inAllowlist, Instant installedAt) {}
