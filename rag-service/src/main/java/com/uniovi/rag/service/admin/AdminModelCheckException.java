package com.uniovi.rag.service.admin;

/**
 * Admin-only model validation error (Ollama availability, pull failures, type mismatch).
 */
public final class AdminModelCheckException extends RuntimeException {

    private final String code;

    public AdminModelCheckException(String code, String message) {
        super(message != null ? message : code);
        this.code = code != null ? code : "MODEL_CHECK_FAILED";
    }

    public String code() {
        return code;
    }
}

