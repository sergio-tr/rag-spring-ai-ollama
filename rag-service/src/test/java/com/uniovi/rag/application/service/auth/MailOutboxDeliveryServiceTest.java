package com.uniovi.rag.application.service.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uniovi.rag.configuration.RagAuthMailProperties;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxDeliveryServiceTest {

    @Mock
    private MailOutboxRepository mailOutboxRepository;

    @Mock
    private JavaMailSender mailSender;

    private static MailOutboxEntity newPending(String purpose) {
        MailOutboxEntity e = new MailOutboxEntity();
        e.setCreatedAt(Instant.now());
        e.setPurpose(purpose);
        e.setRecipient("user@example.com");
        e.setSubject("Subject");
        e.setBodyText("Body");
        return e;
    }

    private static RagAuthMailProperties mailProps() {
        RagAuthMailProperties props = new RagAuthMailProperties();
        props.setEnabled(true);
        props.setFrom("sender@example.com");
        props.setFromName("RAG App");
        return props;
    }

    private MailOutboxDeliveryService service(EffectiveAuthMailDelivery delivery) {
        return new MailOutboxDeliveryService(mailOutboxRepository, mailSender, delivery, mailProps());
    }

    @Test
    void deliverPending_whenMailDisabled_skipsProcessing() {
        EffectiveAuthMailDelivery delivery =
                new EffectiveAuthMailDelivery(false, EffectiveAuthMailDelivery.ResolvedMode.DISABLED, false, false);
        service(delivery).deliverPending();

        verify(mailOutboxRepository, never()).findTop50BySentAtIsNullOrderByCreatedAtAsc();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void deliverPending_outboxOnly_skipsProcessing() {
        EffectiveAuthMailDelivery delivery =
                new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY, false, true);
        service(delivery).deliverPending();

        verify(mailOutboxRepository, never()).findTop50BySentAtIsNullOrderByCreatedAtAsc();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void deliverPending_smtpMode_success_sendsAndMarksSentAt() {
        MailOutboxEntity pending = newPending("EMAIL_CONFIRMATION");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        EffectiveAuthMailDelivery delivery =
                new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.SMTP, true, true);
        service(delivery).deliverPending();

        verify(mailSender).send(any(MimeMessage.class));
        ArgumentCaptor<MailOutboxEntity> saved = ArgumentCaptor.forClass(MailOutboxEntity.class);
        verify(mailOutboxRepository).save(saved.capture());
        assertThat(saved.getValue().getSentAt()).isNotNull();
    }

    @Test
    void deliverPending_sendFailure_keepsRowRetryable() {
        MailOutboxEntity pending = newPending("PASSWORD_RESET");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        EffectiveAuthMailDelivery delivery =
                new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.SMTP, true, true);
        service(delivery).deliverPending();

        verify(mailSender).send(any(MimeMessage.class));
        verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
        assertThat(pending.getSentAt()).isNull();
    }

    @Test
    void deliverPending_sendFailure_warnLogOmitsEmailBodyAndTokens() {
        MailOutboxEntity pending = newPending("EMAIL_CONFIRMATION");
        pending.setBodyText("Confirm: https://app.example/confirm?token=must-not-appear-in-logs");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        Logger logbackLogger = (Logger) LoggerFactory.getLogger(MailOutboxDeliveryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            EffectiveAuthMailDelivery delivery =
                    new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.SMTP, true, true);
            service(delivery).deliverPending();
        } finally {
            logbackLogger.detachAppender(appender);
            appender.stop();
        }

        String joined =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        assertThat(joined).doesNotContain("must-not-appear-in-logs");
        assertThat(joined).doesNotContain("token=");
    }

    @Test
    void recipientDomain_returnsDomainPartOnly() {
        assertThat(MailOutboxDeliveryService.recipientDomain("user@example.com")).isEqualTo("example.com");
        assertThat(MailOutboxDeliveryService.recipientDomain("  a@b.co  ")).isEqualTo("b.co");
    }

    @Test
    void recipientDomain_malformed_returnsPlaceholder() {
        assertThat(MailOutboxDeliveryService.recipientDomain(null)).isEqualTo("<unknown>");
        assertThat(MailOutboxDeliveryService.recipientDomain("")).isEqualTo("<unknown>");
        assertThat(MailOutboxDeliveryService.recipientDomain("no-at-sign")).isEqualTo("<invalid>");
        assertThat(MailOutboxDeliveryService.recipientDomain("@nodomain")).isEqualTo("<invalid>");
    }

    @Test
    void deliverPending_blankMailFrom_keepsSentAtNull_logsAddressException() {
        MailOutboxEntity pending = newPending("EMAIL_CONFIRMATION");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));

        RagAuthMailProperties props = mailProps();
        props.setFrom("   ");

        Logger logbackLogger = (Logger) LoggerFactory.getLogger(MailOutboxDeliveryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            EffectiveAuthMailDelivery delivery =
                    new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.SMTP, true, true);
            new MailOutboxDeliveryService(mailOutboxRepository, mailSender, delivery, props).deliverPending();
        } finally {
            logbackLogger.detachAppender(appender);
            appender.stop();
        }

        verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
        assertThat(pending.getSentAt()).isNull();
        String joined =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        assertThat(joined).contains("rag.auth.mail.from is blank");
        assertThat(joined).doesNotContain("token=");
    }

    @Test
    void deliverPending_mailAuthenticationFailure_logsWithoutEmbeddedProviderSecret() {
        MailOutboxEntity pending = newPending("PASSWORD_RESET");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        String fakeSecretFragment = "FAKE_SMTP_SECRET_FRAGMENT_FOR_TEST";
        doThrow(new MailAuthenticationException(
                        new AuthenticationFailedException("535 BadCredentials " + fakeSecretFragment)))
                .when(mailSender)
                .send(any(MimeMessage.class));

        Logger logbackLogger = (Logger) LoggerFactory.getLogger(MailOutboxDeliveryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);

        try {
            EffectiveAuthMailDelivery delivery =
                    new EffectiveAuthMailDelivery(true, EffectiveAuthMailDelivery.ResolvedMode.SMTP, true, true);
            service(delivery).deliverPending();
        } finally {
            logbackLogger.detachAppender(appender);
            appender.stop();
        }

        verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
        String joined =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        assertThat(joined).contains("MailAuthenticationException");
        assertThat(joined).doesNotContain(fakeSecretFragment);
        assertThat(joined).doesNotContain("Bearer ");
        assertThat(joined).doesNotContain("eyJ");
    }
}
