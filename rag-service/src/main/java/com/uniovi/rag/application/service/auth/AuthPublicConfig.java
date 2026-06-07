package com.uniovi.rag.application.service.auth;

/** Public auth capabilities exposed to the webapp (no secrets). */
public record AuthPublicConfig(
        boolean emailConfirmationEnabled,
        boolean passwordResetEnabled,
        /** {@code disabled}, {@code outbox-only}, or {@code smtp}. */
        String mailDeliveryMode) {
}
