package com.uniovi.rag.application.service.auth;

/**
 * Resolved auth mail behaviour exposed to services and the public auth config API.
 */
public record EffectiveAuthMailDelivery(
        boolean mailEnabled,
        ResolvedMode resolvedMode,
        boolean smtpConfigured,
        boolean javaMailSenderAvailable) {

    public enum ResolvedMode {
        /** {@code rag.auth.mail.enabled=false} - no outbox rows for auth emails. */
        DISABLED,
        /** Rows are queued; SMTP sweep is off (dev / e2e). */
        OUTBOX_ONLY,
        /** Rows are queued and delivered via SMTP when {@code JavaMailSender} is available. */
        SMTP
    }

    /** Public API / UI label for {@link #resolvedMode()}. */
    public String publicDeliveryMode() {
        return switch (resolvedMode) {
            case DISABLED -> "disabled";
            case OUTBOX_ONLY -> "outbox-only";
            case SMTP -> "smtp";
        };
    }

    public boolean shouldRunSmtpSweep() {
        return mailEnabled && resolvedMode == ResolvedMode.SMTP && javaMailSenderAvailable;
    }
}
