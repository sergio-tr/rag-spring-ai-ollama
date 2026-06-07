package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.auth.EffectiveAuthMailDelivery;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class AuthMailConfigurationTest {

    @Test
    void auto_withoutSmtpHost_resolvesToOutboxOnly() {
        MockEnvironment env = new MockEnvironment();
        RagAuthMailProperties props = enabledProps(AuthMailDeliveryMode.AUTO);

        EffectiveAuthMailDelivery delivery =
                new AuthMailConfiguration().effectiveAuthMailDelivery(props, env, mailSenderProvider(null));

        assertThat(delivery.resolvedMode()).isEqualTo(EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY);
        assertThat(delivery.shouldRunSmtpSweep()).isFalse();
        assertThat(delivery.publicDeliveryMode()).isEqualTo("outbox-only");
    }

    @Test
    void auto_withSmtpHostAndMailSender_resolvesToSmtp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.mail.host", "smtp.example.com");
        RagAuthMailProperties props = enabledProps(AuthMailDeliveryMode.AUTO);
        JavaMailSender sender = Mockito.mock(JavaMailSender.class);

        EffectiveAuthMailDelivery delivery =
                new AuthMailConfiguration().effectiveAuthMailDelivery(props, env, mailSenderProvider(sender));

        assertThat(delivery.resolvedMode()).isEqualTo(EffectiveAuthMailDelivery.ResolvedMode.SMTP);
        assertThat(delivery.shouldRunSmtpSweep()).isTrue();
    }

    @Test
    void smtp_withoutSmtpHost_failsStartup() {
        MockEnvironment env = new MockEnvironment();
        RagAuthMailProperties props = enabledProps(AuthMailDeliveryMode.SMTP);

        assertThatThrownBy(() ->
                        new AuthMailConfiguration()
                                .effectiveAuthMailDelivery(props, env, mailSenderProvider(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    void smtp_withHostButNoMailSender_failsStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.mail.host", "smtp.example.com");
        RagAuthMailProperties props = enabledProps(AuthMailDeliveryMode.SMTP);

        assertThatThrownBy(() ->
                        new AuthMailConfiguration()
                                .effectiveAuthMailDelivery(props, env, mailSenderProvider(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JavaMailSender");
    }

    @Test
    void outboxOnly_neverRunsSmtpSweep() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.mail.host", "smtp.example.com");
        RagAuthMailProperties props = enabledProps(AuthMailDeliveryMode.OUTBOX_ONLY);
        JavaMailSender sender = Mockito.mock(JavaMailSender.class);

        EffectiveAuthMailDelivery delivery =
                new AuthMailConfiguration().effectiveAuthMailDelivery(props, env, mailSenderProvider(sender));

        assertThat(delivery.resolvedMode()).isEqualTo(EffectiveAuthMailDelivery.ResolvedMode.OUTBOX_ONLY);
        assertThat(delivery.shouldRunSmtpSweep()).isFalse();
    }

    private static RagAuthMailProperties enabledProps(AuthMailDeliveryMode mode) {
        RagAuthMailProperties props = new RagAuthMailProperties();
        props.setEnabled(true);
        props.setDeliveryMode(mode);
        props.setFrom("no-reply@example.com");
        return props;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> mailSenderProvider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
