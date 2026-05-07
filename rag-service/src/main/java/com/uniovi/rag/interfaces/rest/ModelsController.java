package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.interfaces.rest.dto.SelectableModelDto;
import com.uniovi.rag.service.model.ModelsCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes LLM / embedding models: DB allowlist vs Ollama {@code /api/tags}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/models")
public class ModelsController {

    private final ModelsCatalogService modelsCatalogService;

    public ModelsController(ModelsCatalogService modelsCatalogService) {
        this.modelsCatalogService = modelsCatalogService;
    }

    @GetMapping(params = "type")
    public List<SelectableModelDto> listByType(@RequestParam("type") String type) {
        return modelsCatalogService.listSelectableByType(type);
    }

    @GetMapping
    public ModelsCatalogResponseDto list() {
        return modelsCatalogService.buildCatalog();
    }
}
