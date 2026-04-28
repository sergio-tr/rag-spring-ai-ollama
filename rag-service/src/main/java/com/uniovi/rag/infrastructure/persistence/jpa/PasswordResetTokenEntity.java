package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity extends AbstractUserTokenEntity {

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "request_user_agent", length = 512)
    private String requestUserAgent;

    public PasswordResetTokenEntity() {
        // JPA requires a no-arg constructor; not used by application code.
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getRequestUserAgent() {
        return requestUserAgent;
    }

    public void setRequestUserAgent(String requestUserAgent) {
        this.requestUserAgent = requestUserAgent;
    }
}

