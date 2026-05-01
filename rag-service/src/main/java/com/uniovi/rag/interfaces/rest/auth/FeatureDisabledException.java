package com.uniovi.rag.interfaces.rest.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Explicit "feature disabled" signal for endpoints that must not silently succeed when disabled.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class FeatureDisabledException extends RuntimeException {

    private final String code;
    private final String publicMessage;

    public FeatureDisabledException(String code, String publicMessage) {
        super(code + ": " + publicMessage);
        this.code = code;
        this.publicMessage = publicMessage;
    }

    public String getCode() {
        return code;
    }

    public String getPublicMessage() {
        return publicMessage;
    }
}

