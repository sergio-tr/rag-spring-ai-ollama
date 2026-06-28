package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.interfaces.rest.dto.CreateRagPresetRequest;
import com.uniovi.rag.interfaces.rest.dto.RagPresetDto;
import com.uniovi.rag.interfaces.rest.dto.UpdateRagPresetRequest;
import com.uniovi.rag.security.RagPrincipal;
import com.uniovi.rag.application.service.preset.PresetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/presets")
public class PresetController {

    private final PresetService presetService;

    public PresetController(PresetService presetService) {
        this.presetService = presetService;
    }

    @GetMapping
    public List<RagPresetDto> list(@AuthenticationPrincipal RagPrincipal principal) {
        return presetService.list(principal.userId());
    }

    @GetMapping("/{presetId}")
    public RagPresetDto get(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID presetId) {
        return presetService.get(principal.userId(), presetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RagPresetDto create(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody CreateRagPresetRequest body) {
        return presetService.create(principal.userId(), body);
    }

    @PutMapping("/{presetId}")
    public RagPresetDto update(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID presetId,
            @Valid @RequestBody UpdateRagPresetRequest body) {
        return presetService.update(principal.userId(), presetId, body);
    }

    @DeleteMapping("/{presetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID presetId) {
        presetService.delete(principal.userId(), presetId);
    }
}
