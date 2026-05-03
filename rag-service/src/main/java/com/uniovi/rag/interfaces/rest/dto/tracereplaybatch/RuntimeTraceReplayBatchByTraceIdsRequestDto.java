package com.uniovi.rag.interfaces.rest.dto.tracereplaybatch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * P28 route 1 body: {@code traceIds} only (strict schema — extra JSON keys fail deserialization).
 */
public record RuntimeTraceReplayBatchByTraceIdsRequestDto(
        @JsonProperty(required = true) List<UUID> traceIds) {}
