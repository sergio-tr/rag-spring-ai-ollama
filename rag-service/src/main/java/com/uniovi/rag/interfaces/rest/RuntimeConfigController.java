package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.runtime.config.RuntimeConfigCapabilitiesService;
import com.uniovi.rag.application.service.runtime.config.RuntimeConfigValidationService;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import com.uniovi.rag.interfaces.rest.mapper.RuntimeConfigRestMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateRequest;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidateResponse;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${rag.api.product-base-path}/runtime-config")
public class RuntimeConfigController {

    private final RuntimeConfigCapabilitiesService capabilitiesService;
    private final RuntimeConfigValidationService validationService;

    public RuntimeConfigController(
            RuntimeConfigCapabilitiesService capabilitiesService,
            RuntimeConfigValidationService validationService) {
        this.capabilitiesService = capabilitiesService;
        this.validationService = validationService;
    }

    @GetMapping("/capabilities")
    public RuntimeConfigCapabilitiesResponse capabilities(@AuthenticationPrincipal RagPrincipal principal) {
        // User-specific capabilities may be added later (e.g. role-gated tools). For now this is global.
        return RuntimeConfigRestMapper.toCapabilitiesResponse(capabilitiesService.getCapabilities());
    }

    @PostMapping("/validate")
    public RuntimeConfigValidateResponse validate(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody RuntimeConfigValidateRequest body) {
        return validationService.validate(principal.userId(), body);
    }
}

