package com.uniovi.rag.interfaces.rest.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        String locale,
        Boolean acceptedPrivacyPolicy,
        Boolean acceptedTerms,
        String privacyPolicyVersion,
        String termsVersion) {
}
