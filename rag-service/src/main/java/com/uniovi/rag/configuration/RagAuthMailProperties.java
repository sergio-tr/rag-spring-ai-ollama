package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication email outbox and SMTP delivery settings ({@code rag.auth.mail.*}).
 */
@ConfigurationProperties(prefix = "rag.auth.mail")
@Validated
public class RagAuthMailProperties {

    private boolean enabled = false;
    private String from = "no-reply@local.test";
    private String fromName = "RAG App";
    private AuthMailDeliveryMode deliveryMode = AuthMailDeliveryMode.AUTO;
    private long deliveryIntervalMs = 15_000L;
    private long deliveryInitialDelayMs = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public AuthMailDeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(AuthMailDeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode != null ? deliveryMode : AuthMailDeliveryMode.AUTO;
    }

    public long getDeliveryIntervalMs() {
        return deliveryIntervalMs;
    }

    public void setDeliveryIntervalMs(long deliveryIntervalMs) {
        this.deliveryIntervalMs = deliveryIntervalMs;
    }

    public long getDeliveryInitialDelayMs() {
        return deliveryInitialDelayMs;
    }

    public void setDeliveryInitialDelayMs(long deliveryInitialDelayMs) {
        this.deliveryInitialDelayMs = deliveryInitialDelayMs;
    }
}
