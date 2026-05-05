package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record MetricsCompareRequestDto(
        List<UUID> runIds,
        List<String> queryTypes,
        List<String> difficulties) {}

