package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request body for {@code POST /v1/embeddings}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenAiEmbeddingRequest(
        String model,
        Object input,
        @JsonProperty("encoding_format") String encodingFormat,
        Integer dimensions,
        String user) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiEmbeddingResponse(List<OpenAiEmbeddingDataDto> data, String model, OpenAiUsageDto usage) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiEmbeddingDataDto(Integer index, List<Double> embedding) {}
