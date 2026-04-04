package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ConfigProfileApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ConfigProfileResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConfigProfileRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchConfigProfileRequest;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/config/profiles")
public class ConfigProfileController {

    private final ConfigProfileApplicationService configProfileApplicationService;

    public ConfigProfileController(ConfigProfileApplicationService configProfileApplicationService) {
        this.configProfileApplicationService = configProfileApplicationService;
    }

    @GetMapping
    public List<ConfigProfileResponseDto> list(@AuthenticationPrincipal RagPrincipal principal) {
        return configProfileApplicationService.list(principal.userId());
    }

    @GetMapping("/{profileId}")
    public ConfigProfileResponseDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID profileId) {
        return configProfileApplicationService.get(principal.userId(), profileId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigProfileResponseDto create(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody CreateConfigProfileRequest body) {
        return configProfileApplicationService.create(principal.userId(), principal.roleName(), body);
    }

    @PatchMapping("/{profileId}")
    public ConfigProfileResponseDto patch(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID profileId,
            @RequestBody PatchConfigProfileRequest body) {
        return configProfileApplicationService.patch(principal.userId(), principal.roleName(), profileId, body);
    }
}
