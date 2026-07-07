package com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * P25 route 1 body: {@code traceIds} only (strict schema - extra JSON keys fail deserialization).
 */
public record RuntimeTraceReplayComparisonBatchByTraceIdsRequestDto(
        @JsonProperty(required = true) List<UUID> traceIds) {}
