package com.uniovi.rag.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

final class RuntimeTraceImportRequestSupport {

    private RuntimeTraceImportRequestSupport() {
    }

    static Optional<byte[]> readZipBody(HttpServletRequest request, long maxBytes) {
        String rawCt = request.getContentType();
        if (rawCt == null || rawCt.isBlank()) {
            return Optional.empty();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(rawCt.trim());
        } catch (InvalidMediaTypeException ex) {
            return Optional.empty();
        }
        if (!"application".equals(mediaType.getType())
                || !"zip".equals(mediaType.getSubtype())
                || !mediaType.getParameters().isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0 || body.length > maxBytes) {
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
