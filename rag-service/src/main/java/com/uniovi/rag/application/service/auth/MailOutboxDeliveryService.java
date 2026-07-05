package com.uniovi.rag.application.service.auth;

import com.uniovi.rag.configuration.RagAuthMailProperties;
import com.uniovi.rag.infrastructure.persistence.MailOutboxRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.MailOutboxEntity;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers queued authentication emails from {@code mail_outbox} through SMTP when delivery mode is SMTP.
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
    private final EffectiveAuthMailDelivery delivery;
    private final String mailFrom;
    private final String mailFromName;

    public MailOutboxDeliveryService(
            MailOutboxRepository mailOutboxRepository,
            @Nullable JavaMailSender mailSender,
            EffectiveAuthMailDelivery delivery,
            RagAuthMailProperties mailProperties) {
        this.mailOutboxRepository = mailOutboxRepository;
        this.mailSender = mailSender;
        this.delivery = delivery;
        this.mailFrom = mailProperties.getFrom() != null ? mailProperties.getFrom().trim() : "";
        this.mailFromName =
                mailProperties.getFromName() != null && !mailProperties.getFromName().isBlank()
                        ? mailProperties.getFromName().trim()
                        : "RAG App";
    }

    @PostConstruct
    void logResolvedDeliveryMode() {
        if (!delivery.mailEnabled()) {
            return;
        }
        if (delivery.resolvedMode() == EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY) {
            return;
        }
        if (delivery.resolvedMode() == EffectiveAuthMailDelivery.ResolvedMode.SMTP && mailFrom.isBlank()) {
            log.warn(
                    "Auth mail SMTP delivery is active but rag.auth.mail.from is blank; "
                            + "JavaMail AddressException may occur. Set RAG_AUTH_MAIL_FROM.");
        }
    }

    @Scheduled(
            fixedDelayString = "${rag.auth.mail.delivery-interval-ms:15000}",
            initialDelayString = "${rag.auth.mail.delivery-initial-delay-ms:5000}")
    @Transactional
    public void deliverPending() {
        if (!delivery.shouldRunSmtpSweep()) {
            return;
        }
        List<MailOutboxEntity> pending = mailOutboxRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc();
        for (MailOutboxEntity entry : pending) {
            deliverSingle(entry);
        }
    }

    void deliverSingle(MailOutboxEntity entry) {
        if (mailSender == null) {
            return;
        }
        if (mailFrom.isBlank()) {
            log.warn(
                    "Mail outbox delivery skipped; rag.auth.mail.from is blank (id={}, purpose={}, recipientDomain={})",
                    entry.getId(),
                    entry.getPurpose(),
                    recipientDomain(entry.getRecipient()));
            return;
        }
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
            log.info(
                    "Mail outbox row delivered (id={}, purpose={}, recipientDomain={})",
                    entry.getId(),
                    entry.getPurpose(),
                    recipientDomain(entry.getRecipient()));
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.warn(
                    "Mail outbox delivery failed; row remains pending (id={}, purpose={}, recipientDomain={}, error={}: {})",
                    entry.getId(),
                    entry.getPurpose(),
                    recipientDomain(entry.getRecipient()),
                    ex.getClass().getSimpleName(),
                    truncateForLog(ex.getMessage()));
        }
    }

    /** Domain part only - avoids logging full recipient addresses in operational logs. */
    static String recipientDomain(@Nullable String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "<unknown>";
        }
        String trimmed = recipient.trim();
        int at = trimmed.lastIndexOf('@');
        if (at < 1 || at == trimmed.length() - 1) {
            return "<invalid>";
        }
        return trimmed.substring(at + 1);
    }

    private static String truncateForLog(@Nullable String message) {
        if (message == null) {
            return "<no message>";
        }
        String single = message.replaceAll("\\s+", " ").trim();
        return single.length() <= 240 ? single : single.substring(0, 240) + "…";
    }
}
