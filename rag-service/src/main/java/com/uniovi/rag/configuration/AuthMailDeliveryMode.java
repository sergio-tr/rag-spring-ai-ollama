package com.uniovi.rag.configuration;

/**
 * How queued {@code mail_outbox} rows are delivered when {@link RagAuthMailProperties#isEnabled()} is true.
 *
 * <ul>
 *   <li>{@link #AUTO} — use SMTP when {@code spring.mail.host} is set, otherwise outbox-only (explicit dev fallback).
 *   <li>{@link #SMTP} — require SMTP configuration and a {@code JavaMailSender} bean; fail startup if missing.
 *   <li>{@link #OUTBOX_ONLY} — queue rows only; no SMTP sweep (local dev / e2e inspection via admin outbox).
 * </ul>
 */
public enum AuthMailDeliveryMode {
    AUTO,
    SMTP,
    OUTBOX_ONLY
}
