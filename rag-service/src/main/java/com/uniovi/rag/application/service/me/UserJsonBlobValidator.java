package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Server-side validation for user preference/personalization JSON blobs (schema v1).
 */
public final class UserJsonBlobValidator {

    private static final int MAX_SERIALIZED_BYTES = 256_000;

    private UserJsonBlobValidator() {
    }

    public static void validateV1Map(Map<String, Object> map, ObjectMapper objectMapper) {
        if (map == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must contain a JSON object");
        }
        if (map.size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many keys");
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(map);
            if (bytes.length > MAX_SERIALIZED_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload too large");
            }
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON values");
        }
    }
}
