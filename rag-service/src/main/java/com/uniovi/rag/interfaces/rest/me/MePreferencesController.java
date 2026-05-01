package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.UserMePreferenceService;
import com.uniovi.rag.interfaces.rest.dto.me.MePreferencesResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPreferencesRequest;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "Authenticated user preferences (canonical JSON store)")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/preferences")
public class MePreferencesController {

    private final UserMePreferenceService userMePreferenceService;

    public MePreferencesController(UserMePreferenceService userMePreferenceService) {
        this.userMePreferenceService = userMePreferenceService;
    }

    @GetMapping
    public MePreferencesResponse get(@AuthenticationPrincipal RagPrincipal principal) {
        return userMePreferenceService.get(principal.userId());
    }

    @PutMapping
    public MePreferencesResponse put(
            @AuthenticationPrincipal RagPrincipal principal, @Valid @RequestBody MePutPreferencesRequest body) {
        return userMePreferenceService.put(principal.userId(), body);
    }
}
