package com.uniovi.rag.interfaces.rest.admin;

import com.uniovi.rag.application.service.llm.LlmManualHealthCheckService;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminLlmHealthCheckResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual LLM connectivity probe for operators. Not invoked on normal chat/RAG requests.
 */
@RestController
@RequestMapping("${rag.api.product-base-path}/admin")
public class AdminLlmHealthController {

    private final LlmManualHealthCheckService llmManualHealthCheckService;

    public AdminLlmHealthController(LlmManualHealthCheckService llmManualHealthCheckService) {
        this.llmManualHealthCheckService = llmManualHealthCheckService;
    }

    @PostMapping("/llm/health-check")
    public AdminLlmHealthCheckResponse healthCheck() {
        LlmManualHealthCheckService.LlmHealthCheckResult result =
                llmManualHealthCheckService.checkApplicationDefaults();
        return new AdminLlmHealthCheckResponse(
                result.provider() != null ? result.provider().name() : null,
                result.model(),
                result.baseUrl(),
                result.operation(),
                result.healthy(),
                result.latencyMs(),
                result.status(),
                result.message());
    }
}
