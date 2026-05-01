package com.uniovi.rag.interfaces.rest.auth.dto;

public record ForgotPasswordResponse(String status, String message) {
    public static ForgotPasswordResponse neutral() {
        return new ForgotPasswordResponse(
                "REQUEST_ACCEPTED",
                "If an account exists for that email, a reset link will be sent");
    }
}
