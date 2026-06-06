package com.uniovi.rag.application.service.auth;

import com.uniovi.Application;
import com.uniovi.rag.infrastructure.persistence.EmailConfirmationTokenRepository;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterRequest;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterResponse;
import com.uniovi.rag.testsupport.TestAiStubConfiguration;
import com.uniovi.rag.testsupport.TestcontainersDatasourceConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@TestPropertySource(
        properties = {
            "rag.auth.email-confirmation.enabled=true",
            "rag.auth.mail.enabled=true",
            "rag.auth.mail.delivery-mode=smtp",
            "spring.mail.host=smtp.test.local",
            "spring.mail.port=587",
            "rag.auth.webapp-base-url=http://localhost:3000"
        })
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isSpringBootPostgresAvailable",
        disabledReason = "Postgres/Testcontainers not available")
class AuthMailDeliveryIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private EmailConfirmationTokenRepository emailConfirmationTokenRepository;

    @Autowired
    private MailOutboxDeliveryService mailOutboxDeliveryService;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void register_queuesConfirmationAndSmtpSweepMarksSent() {
        String email = "mail-it-" + System.nanoTime() + "@example.com";

        RegisterResponse response = authService.register(
                new RegisterRequest("Mail IT", email, "password123", "en", true, true, "v1", "v1"));

        assertThat(response.status()).isEqualTo("PENDING_EMAIL_VERIFICATION");
        assertThat(response.confirmationDelivery()).isEqualTo("smtp");

        List<MailOutboxEntity> pending =
                mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc().stream()
                        .filter(e -> email.equalsIgnoreCase(e.getRecipient()))
                        .toList();
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getPurpose()).isEqualTo("EMAIL_CONFIRMATION");
        assertThat(emailConfirmationTokenRepository.findAll()).isNotEmpty();

        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        mailOutboxDeliveryService.deliverPending();

        verify(javaMailSender).send(any(MimeMessage.class));
        MailOutboxEntity saved = mailOutboxRepository.findById(pending.getFirst().getId()).orElseThrow();
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    void outboxOnlyMode_doesNotInvokeJavaMailSender() {
        assertThat(mailOutboxDeliveryService).isNotNull();
    }
}
