package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin-only view for inspecting mail outbox in non-production profiles (dev/e2e).
 * Contains email bodies which may include one-time tokens.
 */
public record AdminMailOutboxEntryDto(
        UUID id,
        Instant createdAt,
        String purpose,
        String recipient,
        String subject,
        String bodyText,
        Instant sentAt) {

    public static AdminMailOutboxEntryDto fromEntity(MailOutboxEntity e) {
        return new AdminMailOutboxEntryDto(
                e.getId(),
                e.getCreatedAt(),
                e.getPurpose(),
                e.getRecipient(),
                e.getSubject(),
                e.getBodyText(),
                e.getSentAt());
    }
}

