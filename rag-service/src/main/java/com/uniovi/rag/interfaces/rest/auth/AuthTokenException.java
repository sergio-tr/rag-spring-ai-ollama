package com.uniovi.rag.interfaces.rest.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AuthTokenException extends RuntimeException {

    private final String code;
    private final String publicMessage;

    public AuthTokenException(String code, String publicMessage) {
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

