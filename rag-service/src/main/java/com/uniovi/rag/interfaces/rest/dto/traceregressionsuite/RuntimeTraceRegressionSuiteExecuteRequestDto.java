package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Route 1 body - explicit discriminated suite entries (P31). */
public record RuntimeTraceRegressionSuiteExecuteRequestDto(
        @JsonProperty(value = "entries", required = true) List<RuntimeTraceRegressionSuiteEntryRequestDto> entries) {}
