package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.llm.catalog.LabEvaluationModelsService;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.lab.LabEvaluationModelsResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Lab", description = "Lab evaluation model catalog")
@RestController
@RequestMapping("${rag.api.product-base-path}/lab")
public class LabEvaluationModelsController {

    private final LabEvaluationModelsService labEvaluationModelsService;

    public LabEvaluationModelsController(LabEvaluationModelsService labEvaluationModelsService) {
        this.labEvaluationModelsService = labEvaluationModelsService;
    }

    @GetMapping("/evaluation-models")
    public LabEvaluationModelsResponseDto list(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam LlmModelCapability capability,
            @RequestParam(defaultValue = "true") boolean includeRuntimeStatus) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return labEvaluationModelsService.listForUser(principal.userId(), capability, includeRuntimeStatus);
    }
}
