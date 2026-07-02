package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.embedding.EmbeddingDefaultsResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.interfaces.rest.dto.me.embedding.MeEffectiveEmbeddingDefaultsResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Me", description = "Resolved embedding defaults for Settings")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/embedding")
public class MeEffectiveEmbeddingDefaultsController {

    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final EmbeddingDefaultsResolver embeddingDefaultsResolver;

    public MeEffectiveEmbeddingDefaultsController(
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            EmbeddingDefaultsResolver embeddingDefaultsResolver) {
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.embeddingDefaultsResolver = embeddingDefaultsResolver;
    }

    @GetMapping("/effective-defaults")
    public MeEffectiveEmbeddingDefaultsResponseDto get(@AuthenticationPrincipal RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ResolvedLlmConfig resolved = resolvedLlmConfigResolver.resolve(principal.userId(), null, null);
        EmbeddingDefaultsResolver.EffectiveEmbeddingDefaults defaults =
                embeddingDefaultsResolver.resolve(principal.userId(), null, Map.of());
        return new MeEffectiveEmbeddingDefaultsResponseDto(
                resolved.embeddingProvider(),
                defaults.embeddingModel(),
                defaults.embeddingOptions(),
                defaults.retrievalOptions(),
                defaults.indexingOptions());
    }
}
