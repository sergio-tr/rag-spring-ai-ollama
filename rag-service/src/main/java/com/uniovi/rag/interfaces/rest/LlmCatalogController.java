package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Properties-backed LLM model catalog ({@code rag.llm.*.available-*-models}).
 * Runtime discovery enriches status only; never adds models outside configuration.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/llm")
public class LlmCatalogController {

    private final LlmCatalogApiService llmCatalogApiService;

    public LlmCatalogController(LlmCatalogApiService llmCatalogApiService) {
        this.llmCatalogApiService = llmCatalogApiService;
    }

    @GetMapping("/catalog")
    public LlmCatalogResponseDto catalog(
            @RequestParam(required = false) LlmProvider provider,
            @RequestParam(required = false) LlmModelCapability capability,
            @RequestParam(required = false) Boolean selectable,
            @RequestParam(required = false, defaultValue = "false") boolean includeRuntimeStatus) {
        return llmCatalogApiService.listCatalog(provider, capability, selectable, includeRuntimeStatus);
    }
}
