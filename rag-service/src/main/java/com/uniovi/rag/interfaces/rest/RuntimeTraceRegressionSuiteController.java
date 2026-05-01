package com.uniovi.rag.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteService;
import com.uniovi.rag.configuration.RegressionSuiteRestJacksonConfiguration;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteResponseDto;
import com.uniovi.rag.security.RagPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P31: two POST routes delegating only to {@link RuntimeTraceRegressionSuiteService#execute}.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}")
public class RuntimeTraceRegressionSuiteController {

    private final RuntimeTraceRegressionSuiteService suiteService;
    private final ObjectMapper strictRegressionSuiteMapper;

    public RuntimeTraceRegressionSuiteController(
            RuntimeTraceRegressionSuiteService suiteService,
            @Qualifier(RegressionSuiteRestJacksonConfiguration.REGRESSION_SUITE_STRICT_OBJECT_MAPPER)
                    ObjectMapper strictRegressionSuiteMapper) {
        this.suiteService = suiteService;
        this.strictRegressionSuiteMapper = strictRegressionSuiteMapper;
    }

    @PostMapping(value = "/runtime-traces/regression-suite", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeExplicitSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedExplicit> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseExplicit(body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return respond(suiteService.execute(parsed.get().domainRequest()));
    }

    @PostMapping(
            value = "/conversations/{conversationId}/runtime-traces/regression-suite",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> executeConversationScopedSuite(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable("conversationId") String conversationIdRaw,
            @RequestBody String body,
            HttpServletRequest request) {
        if (request.getQueryString() != null) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = principal.userId();
        Optional<RuntimeTraceRegressionSuiteHttpAdapter.ValidatedConversationScoped> parsed =
                RuntimeTraceRegressionSuiteHttpAdapter.parseConversationScoped(
                        conversationIdRaw, body, strictRegressionSuiteMapper, userId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return respond(suiteService.execute(parsed.get().domainRequest()));
    }

    private static ResponseEntity<RuntimeTraceRegressionSuiteResponseDto> respond(RuntimeTraceRegressionSuiteResult result) {
        if (result.suiteOutcome() == RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(RuntimeTraceRegressionSuiteResponseDto.fromResult(result));
    }
}
