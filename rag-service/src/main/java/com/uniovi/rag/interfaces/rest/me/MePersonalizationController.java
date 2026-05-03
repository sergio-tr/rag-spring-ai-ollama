package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.UserMePersonalizationService;
import com.uniovi.rag.interfaces.rest.dto.me.MePersonalizationResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPersonalizationRequest;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "User personalization JSON (canonical store)")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/personalization")
public class MePersonalizationController {

    private final UserMePersonalizationService userMePersonalizationService;

    public MePersonalizationController(UserMePersonalizationService userMePersonalizationService) {
        this.userMePersonalizationService = userMePersonalizationService;
    }

    @GetMapping
    public MePersonalizationResponse get(@AuthenticationPrincipal RagPrincipal principal) {
        return userMePersonalizationService.get(principal.userId());
    }

    @PutMapping
    public MePersonalizationResponse put(
            @AuthenticationPrincipal RagPrincipal principal,
            @Valid @RequestBody MePutPersonalizationRequest body) {
        return userMePersonalizationService.put(principal.userId(), body);
    }
}
