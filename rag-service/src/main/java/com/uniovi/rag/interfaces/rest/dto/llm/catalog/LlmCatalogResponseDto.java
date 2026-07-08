package com.uniovi.rag.interfaces.rest.dto.llm.catalog;

import java.util.List;

/** GET {@code {product}/llm/catalog} response envelope. */
public record LlmCatalogResponseDto(List<LlmCatalogModelDto> models) {}
