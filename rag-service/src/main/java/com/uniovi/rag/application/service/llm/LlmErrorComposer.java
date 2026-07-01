package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.interfaces.rest.support.ConnectivityFailureDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Composes user-facing apology messages after query failures using the effective provider/model (BL-011).
 */
@Service
public class LlmErrorComposer {

    private static final Logger log = LoggerFactory.getLogger(LlmErrorComposer.class);

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    public LlmErrorComposer(ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
    }

    public ResolvedLlmConfig effectiveConfig() {
        return secondaryLlmExecutor.effectiveConfig();
    }

    public String composeApologyForQueryFailure(String query, @Nullable Throwable cause) {
        ResolvedLlmConfig config = effectiveConfig();
        if (cause != null && ConnectivityFailureDetector.isConnectivityFailure(cause)) {
            return LlmFallbackErrorComposer.connectivityUnavailable(config);
        }
        if (cause != null && ConnectivityFailureDetector.isOllamaModelMissingFailure(cause)) {
            return LlmFallbackErrorComposer.modelNotInstalled(config);
        }

        String prompt =
                String.format(
                        """
                        The user asked (in any language): "%s"

                        An error occurred while processing this query.

                        Respond with a short message in the EXACT SAME LANGUAGE as the question,
                        apologizing for the error and asking the user to try again.
                        Be concise and polite.
                        Do not repeat the question.
                        """,
                        query != null ? query : "");

        try {
            String response = secondaryLlmExecutor.complete("error-composer", null, prompt);
            if (response != null && !response.isBlank()) {
                return response.trim();
            }
        } catch (Exception e) {
            if (ConnectivityFailureDetector.isConnectivityFailure(e)) {
                log.warn("Skipping LLM error message: inference backend unreachable");
                return LlmFallbackErrorComposer.connectivityUnavailable(config);
            }
            if (ConnectivityFailureDetector.isOllamaModelMissingFailure(e)) {
                log.warn("Skipping LLM error message: model not installed on provider");
                return LlmFallbackErrorComposer.modelNotInstalled(config);
            }
            log.warn("Error generating error response with LLM", e);
        }

        return LlmFallbackErrorComposer.genericApology();
    }
}
