package com.uniovi.rag.interfaces.rest.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(@NotBlank @Email String email, String locale) {
}

