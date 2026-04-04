package com.uniovi.rag.interfaces.rest.dto.me;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Strong confirmation for irreversible account deletion (async job).
 */
public record AccountDeletionRequest(
        @NotBlank String confirm,
        @NotBlank @Email String email) {}
