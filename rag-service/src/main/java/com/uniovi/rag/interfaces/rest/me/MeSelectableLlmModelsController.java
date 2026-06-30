package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.llm.catalog.MeSelectableLlmModelsService;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeSelectableLlmModelsResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Me", description = "Authenticated user LLM catalog selections")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/llm")
public class MeSelectableLlmModelsController {

    private final MeSelectableLlmModelsService selectableLlmModelsService;

    public MeSelectableLlmModelsController(MeSelectableLlmModelsService selectableLlmModelsService) {
        this.selectableLlmModelsService = selectableLlmModelsService;
    }

    @GetMapping("/selectable-models")
    public MeSelectableLlmModelsResponseDto list(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestParam(defaultValue = "CHAT") LlmModelCapability capability) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return selectableLlmModelsService.listForUser(principal.userId(), capability);
    }
}
