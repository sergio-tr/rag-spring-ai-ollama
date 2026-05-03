package com.uniovi.rag.interfaces.rest.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateRagPresetRequest(
        @Size(max = 255) String name,
        @Size(max = 4000) String description,
        List<String> tags,
        Map<String, Object> values,
        List<PresetProfileRefDto> profileRefs) {}
