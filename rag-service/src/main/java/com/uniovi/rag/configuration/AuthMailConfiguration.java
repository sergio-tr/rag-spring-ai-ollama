package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.auth.EffectiveAuthMailDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * Resolves auth mail delivery mode and validates SMTP prerequisites at startup.
 */
@Configuration
@EnableConfigurationProperties(RagAuthMailProperties.class)
public class AuthMailConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthMailConfiguration.class);

    @Bean
    EffectiveAuthMailDelivery effectiveAuthMailDelivery(
            RagAuthMailProperties mailProperties,
            Environment environment,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        if (!mailProperties.isEnabled()) {
            return new EffectiveAuthMailDelivery(false, EffectiveAuthMailDelivery.ResolvedMode.DISABLED, false, false);
        }

        String smtpHost = environment.getProperty("spring.mail.host");
        boolean smtpHostConfigured = StringUtils.hasText(smtpHost);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        boolean javaMailSenderAvailable = mailSender != null;

        EffectiveAuthMailDelivery.ResolvedMode resolved =
                resolveMode(mailProperties.getDeliveryMode(), smtpHostConfigured);

        if (mailProperties.getDeliveryMode() == AuthMailDeliveryMode.SMTP && !smtpHostConfigured) {
            throw new IllegalStateException(
                    "rag.auth.mail.delivery-mode=smtp requires spring.mail.host (set SPRING_MAIL_HOST). "
                            + "For local development without SMTP, use rag.auth.mail.delivery-mode=outbox-only or auto.");
        }
        if (resolved == EffectiveAuthMailDelivery.ResolvedMode.SMTP && !javaMailSenderAvailable) {
            throw new IllegalStateException(
                    "Auth mail delivery resolved to SMTP but JavaMailSender is unavailable. "
                            + "Set SPRING_MAIL_HOST (and related SPRING_MAIL_* settings) or use "
                            + "rag.auth.mail.delivery-mode=outbox-only.");
        }

        if (resolved == EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY) {
            if (mailProperties.getDeliveryMode() == AuthMailDeliveryMode.AUTO) {
                log.info(
                        "Auth mail delivery-mode=auto resolved to outbox-only (spring.mail.host is not set). "
                                + "Confirmation emails are stored in mail_outbox but are not sent via SMTP. "
                                + "Set SPRING_MAIL_HOST for live delivery or RAG_AUTH_MAIL_DELIVERY_MODE=smtp.");
            } else {
                log.info(
                        "Auth mail delivery-mode=outbox-only - confirmation emails are queued in mail_outbox only "
                                + "(no SMTP sweep).");
            }
        } else if (resolved == EffectiveAuthMailDelivery.ResolvedMode.SMTP) {
            log.info("Auth mail delivery-mode=smtp - mail_outbox rows will be sent via JavaMailSender.");
        }

        if (resolved == EffectiveAuthMailDelivery.ResolvedMode.SMTP
                && !StringUtils.hasText(trim(mailProperties.getFrom()))) {
            log.warn(
                    "Auth mail SMTP delivery is active but rag.auth.mail.from is blank; "
                            + "JavaMail may reject the From address.");
        }

        return new EffectiveAuthMailDelivery(
                true, resolved, smtpHostConfigured, javaMailSenderAvailable);
    }

    private static EffectiveAuthMailDelivery.ResolvedMode resolveMode(
            AuthMailDeliveryMode configured, boolean smtpHostConfigured) {
        return switch (configured) {
            case OUTBOX_ONLY -> EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY;
            case SMTP -> EffectiveAuthMailDelivery.ResolvedMode.SMTP;
            case AUTO -> smtpHostConfigured
                    ? EffectiveAuthMailDelivery.ResolvedMode.SMTP
                    : EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY;
        };
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
