package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.UUID;

public record CompareRunsResponseDto(
        boolean comparable,
        List<String> incompatibilityReasons,
        UUID runA,
        UUID runB) {}
