package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.chat.ProjectCompatiblePresetsService;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.interfaces.rest.dto.ProjectCompatiblePresetsDto;
import com.uniovi.rag.interfaces.rest.mapper.ProjectCompatiblePresetsRestMapper;
import com.uniovi.rag.security.RagPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/compatible-presets")
public class ProjectCompatiblePresetsController {

    private final ProjectCompatiblePresetsService projectCompatiblePresetsService;

    public ProjectCompatiblePresetsController(ProjectCompatiblePresetsService projectCompatiblePresetsService) {
        this.projectCompatiblePresetsService = projectCompatiblePresetsService;
    }

    @GetMapping
    public ProjectCompatiblePresetsDto list(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String embeddingModelId) {
        ProjectCompatiblePresetsCatalog catalog =
                projectCompatiblePresetsService.list(principal.userId(), projectId, embeddingModelId);
        return ProjectCompatiblePresetsRestMapper.toDto(catalog);
    }
}
