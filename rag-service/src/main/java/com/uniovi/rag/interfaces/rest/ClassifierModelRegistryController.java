package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.ActivateClassifierModelRequest;
import com.uniovi.rag.interfaces.rest.dto.ClassifierModelResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.service.classifier.ClassifierModelRegistryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Registry of trained classifier artifacts and explicit per-project activation (writes {@code classifierModelId}).
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/lab/classifier/models")
public class ClassifierModelRegistryController {

    private final ClassifierModelRegistryService classifierModelRegistryService;

    public ClassifierModelRegistryController(ClassifierModelRegistryService classifierModelRegistryService) {
        this.classifierModelRegistryService = classifierModelRegistryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ClassifierModelResponseDto> list(@AuthenticationPrincipal RagPrincipal principal) {
        return classifierModelRegistryService.listForUserWithSync(principal.userId());
    }

    @PostMapping(path = "/{modelId}/activate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ClassifierModelResponseDto activate(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID modelId,
            @RequestBody ActivateClassifierModelRequest body) {
        return classifierModelRegistryService.activateForProject(principal.userId(), body.projectId(), modelId);
    }
}
