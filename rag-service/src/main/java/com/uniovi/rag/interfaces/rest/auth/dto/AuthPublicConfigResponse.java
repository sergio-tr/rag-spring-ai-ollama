package com.uniovi.rag.interfaces.rest.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public auth capabilities for the webapp (no secrets).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthPublicConfigResponse(
        boolean emailConfirmationEnabled,
        boolean passwordResetEnabled,
        /** {@code disabled}, {@code outbox-only}, or {@code smtp}. */
        String mailDeliveryMode) {
}
