package com.uniovi.rag.application.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthPublicConfigService {

    private final EffectiveAuthMailDelivery mailDelivery;
    private final boolean emailConfirmationEnabled;
    private final boolean passwordResetEnabled;

    public AuthPublicConfigService(
            EffectiveAuthMailDelivery mailDelivery,
            @Value("${rag.auth.email-confirmation.enabled:false}") boolean emailConfirmationEnabled,
            @Value("${rag.auth.password-reset.enabled:false}") boolean passwordResetEnabled) {
        this.mailDelivery = mailDelivery;
        this.emailConfirmationEnabled = emailConfirmationEnabled;
        this.passwordResetEnabled = passwordResetEnabled;
    }

    public AuthPublicConfig publicConfig() {
        return new AuthPublicConfig(
                emailConfirmationEnabled, passwordResetEnabled, mailDelivery.publicDeliveryMode());
    }
}
