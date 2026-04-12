package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.Application;
import com.uniovi.rag.infrastructure.persistence.AuditLogRepository;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
            "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
            "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics"
        })
@Import({TestAiStubConfiguration.class, TestcontainersDatasourceConfiguration.class})
@ActiveProfiles("test")
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class AuditLogPersistenceIT {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @Transactional
    void saveAndRoundTrip_persistsAuditRow() {
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        UUID resourceId = UUID.randomUUID();
        AuditLogEntity e =
                AuditLogEntity.create(
                        null,
                        "CONFIG_PROFILE_CREATE",
                        "config_profile",
                        resourceId,
                        Map.of("profileType", "INDEX"),
                        now);
        e = auditLogRepository.save(e);
        assertThat(e.getId()).isNotNull();

        AuditLogEntity loaded = auditLogRepository.findById(e.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(e.getId());
    }
}
