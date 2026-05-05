package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

public record RuntimeConfigCapabilityDto(
        String key,
        String label,
        String description,
        String group,
        boolean implemented,
        boolean configurable,
        List<String> requires,
        List<String> excludes,
        String reasonIfNotImplemented,
        Map<String, Object> options
) {}

