package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.evaluation.LabExperimentalPresetCatalogService;
import com.uniovi.rag.interfaces.rest.dto.ChatPresetCatalogDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.preset.PresetService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat-facing preset catalog.
 * <p>
 * Product presets and experimental presets are intentionally kept distinct in the response so the UI can
 * render two explicit sections without hiding NOT_SUPPORTED items.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class ChatPresetCatalogController {

    private final PresetService presetService;
    private final LabExperimentalPresetCatalogService labExperimentalPresetCatalogService;

    public ChatPresetCatalogController(
            PresetService presetService,
            LabExperimentalPresetCatalogService labExperimentalPresetCatalogService) {
        this.presetService = presetService;
        this.labExperimentalPresetCatalogService = labExperimentalPresetCatalogService;
    }

    @GetMapping("/chat/presets/catalog")
    public ChatPresetCatalogDto catalog(@AuthenticationPrincipal RagPrincipal principal) {
        return new ChatPresetCatalogDto(
                presetService.list(principal.userId()),
                labExperimentalPresetCatalogService.list());
    }
}

