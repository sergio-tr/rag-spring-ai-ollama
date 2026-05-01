package com.uniovi.rag.interfaces.rest.admin.dto;

import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMailOutboxEntryDtoTest {

    @Test
    void fromEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-05-01T10:00:00Z");
        Instant sent = Instant.parse("2026-05-01T10:05:00Z");
        MailOutboxEntity e = new MailOutboxEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setCreatedAt(created);
        e.setPurpose("PASSWORD_RESET");
        e.setRecipient("u@example.com");
        e.setSubject("reset");
        e.setBodyText("token-body");
        e.setSentAt(sent);

        AdminMailOutboxEntryDto dto = AdminMailOutboxEntryDto.fromEntity(e);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.createdAt()).isEqualTo(created);
        assertThat(dto.purpose()).isEqualTo("PASSWORD_RESET");
        assertThat(dto.recipient()).isEqualTo("u@example.com");
        assertThat(dto.subject()).isEqualTo("reset");
        assertThat(dto.bodyText()).isEqualTo("token-body");
        assertThat(dto.sentAt()).isEqualTo(sent);
    }
}
