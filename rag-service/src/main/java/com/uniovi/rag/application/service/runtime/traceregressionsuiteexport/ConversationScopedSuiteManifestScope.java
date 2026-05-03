package com.uniovi.rag.application.service.runtime.traceregressionsuiteexport;

import com.uniovi.rag.interfaces.rest.dto.traceregressionsuite.RuntimeTraceRegressionSuiteConversationExecuteRequestDto;
import java.util.UUID;

/**
 * Manifest {@code scope} for {@link RegressionSuiteExportManifest#selectorType}{@code CONVERSATION_SCOPED_SUITE} (P32).
 */
public record ConversationScopedSuiteManifestScope(
        UUID conversationId, RuntimeTraceRegressionSuiteConversationExecuteRequestDto body) {}
