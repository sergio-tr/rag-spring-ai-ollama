package com.uniovi.rag.infrastructure.persistence.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_confirmation_tokens")
public class EmailConfirmationTokenEntity extends AbstractUserTokenEntity {

    public EmailConfirmationTokenEntity() {
        // JPA requires a no-arg constructor; not used by application code.
    }
}

