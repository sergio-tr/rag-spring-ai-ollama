package com.uniovi.rag.interfaces.rest.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Register response is conditional:
 * - If email confirmation is enabled: no JWTs are issued until verification completes.
 * - Otherwise: returns the same token payload as login.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterResponse(String status, LoginResponse login, String confirmationDelivery) {
}

