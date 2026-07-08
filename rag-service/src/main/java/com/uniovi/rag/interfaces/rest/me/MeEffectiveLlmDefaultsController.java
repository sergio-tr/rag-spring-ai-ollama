package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.interfaces.rest.dto.me.llm.MeEffectiveLlmDefaultsResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Me", description = "Resolved LLM defaults for Settings")
@RestController
@RequestMapping("${rag.api.product-base-path}/me/llm")
public class MeEffectiveLlmDefaultsController {

    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final RagConfigurationResolver ragConfigurationResolver;

    public MeEffectiveLlmDefaultsController(
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            RagConfigurationResolver ragConfigurationResolver) {
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.ragConfigurationResolver = ragConfigurationResolver;
    }

    /**
     * Returns the resolved effective LLM defaults for the current user (application → system → user → project/preset).
     * This is intended for Settings previews and avoids exposing secrets.
     */
    @GetMapping("/effective-defaults")
    public MeEffectiveLlmDefaultsResponseDto get(@AuthenticationPrincipal RagPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ResolvedLlmConfig resolved = resolvedLlmConfigResolver.resolve(principal.userId(), null, null);
        RagConfig rag = ragConfigurationResolver.resolve(principal.userId(), null, null);
        Map<String, Object> additional = new LinkedHashMap<>(resolved.additionalParameters());

        // UI-facing “effective” defaults (do not change runtime behavior):
        // OpenAI-compatible mapper defaults think=false unless explicitly overridden.
        if (resolved.chatProvider() == LlmProvider.OPENAI_COMPATIBLE && !additional.containsKey("think")) {
            additional.put("think", Boolean.FALSE);
        }

        return new MeEffectiveLlmDefaultsResponseDto(
                resolved.chatProvider(),
                resolved.chatModel(),
                rag.classifierModelId(),
                resolved.temperature(),
                Map.copyOf(additional));
    }
}

