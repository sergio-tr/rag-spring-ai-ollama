package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Test
    void deliverPending_whenMailDisabled_skipsProcessing() {
        MailOutboxDeliveryService svc =
                new MailOutboxDeliveryService(mailOutboxRepository, mailSender, false, "no-reply@example.com", "RAG App");

        svc.deliverPending();

        verify(mailOutboxRepository, never()).findTop50BySentAtIsNullOrderByCreatedAtAsc();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void deliverPending_success_sendsAndMarksSentAt() {
        MailOutboxEntity pending = newPending("EMAIL_CONFIRMATION");
        when(mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        MailOutboxDeliveryService svc =
                new MailOutboxDeliveryService(mailOutboxRepository, mailSender, true, "sender@example.com", "RAG App");

        svc.deliverPending();

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

        MailOutboxDeliveryService svc =
                new MailOutboxDeliveryService(mailOutboxRepository, mailSender, true, "sender@example.com", "RAG App");

        svc.deliverPending();

        verify(mailSender).send(any(MimeMessage.class));
        verify(mailOutboxRepository, never()).save(any(MailOutboxEntity.class));
        assertThat(pending.getSentAt()).isNull();
    }
}
