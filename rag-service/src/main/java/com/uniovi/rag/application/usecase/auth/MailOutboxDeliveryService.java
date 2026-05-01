package com.uniovi.rag.application.usecase.auth;

import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers queued authentication emails from {@code mail_outbox} through SMTP.
 *
 * <p>Rows are marked with {@code sent_at} on successful delivery; failed rows are left unsent so they
 * remain observable and retryable in the next sweep.
 */
@Service
public class MailOutboxDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxDeliveryService.class);

    private final MailOutboxRepository mailOutboxRepository;
    @Nullable
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String mailFrom;
    private final String mailFromName;

    public MailOutboxDeliveryService(
            MailOutboxRepository mailOutboxRepository,
            @Nullable JavaMailSender mailSender,
            @Value("${rag.auth.mail.enabled:false}") boolean mailEnabled,
            @Value("${rag.auth.mail.from:no-reply@local.test}") String mailFrom,
            @Value("${rag.auth.mail.from-name:RAG App}") String mailFromName) {
        this.mailOutboxRepository = mailOutboxRepository;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom != null ? mailFrom.trim() : "no-reply@local.test";
        this.mailFromName = mailFromName != null ? mailFromName.trim() : "RAG App";
    }

    @Scheduled(
            fixedDelayString = "${rag.auth.mail.delivery-interval-ms:15000}",
            initialDelayString = "${rag.auth.mail.delivery-initial-delay-ms:5000}")
    @Transactional
    public void deliverPending() {
        if (!mailEnabled) {
            return;
        }
        if (mailSender == null) {
            log.warn("Mail delivery is enabled but JavaMailSender bean is unavailable; skipping outbox sweep");
            return;
        }
        List<MailOutboxEntity> pending = mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc();
        for (MailOutboxEntity entry : pending) {
            deliverSingle(entry);
        }
    }

    void deliverSingle(MailOutboxEntity entry) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            helper.setTo(entry.getRecipient());
            helper.setSubject(entry.getSubject());
            helper.setText(entry.getBodyText(), false);
            helper.setFrom(new InternetAddress(mailFrom, mailFromName, StandardCharsets.UTF_8.name()).toString());
            mailSender.send(msg);

            entry.setSentAt(Instant.now());
            mailOutboxRepository.save(entry);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // The exception message comes from JavaMail / Spring Mail (e.g. Gmail "535-5.7.8 Username
            // and Password not accepted"). It does not contain our credentials. We log it truncated
            // and without the stack trace so operators can act, but logs stay safe.
            log.warn(
                    "Mail outbox delivery failed; row remains pending (id={}, purpose={}, error={}: {})",
                    entry.getId(),
                    entry.getPurpose(),
                    ex.getClass().getSimpleName(),
                    truncateForLog(ex.getMessage()));
        }
    }

    /** Truncates exception messages so SMTP errors stay readable without flooding logs. */
    private static String truncateForLog(@Nullable String message) {
        if (message == null) {
            return "<no message>";
        }
        String single = message.replaceAll("\\s+", " ").trim();
        return single.length() <= 240 ? single : single.substring(0, 240) + "…";
    }
}
