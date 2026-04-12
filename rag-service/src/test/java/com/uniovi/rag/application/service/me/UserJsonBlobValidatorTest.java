package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserJsonBlobValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validateV1Map_null_badRequest() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> UserJsonBlobValidator.validateV1Map(null, objectMapper));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateV1Map_tooManyKeys_badRequest() {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < 201; i++) {
            map.put("k" + i, "v");
        }
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> UserJsonBlobValidator.validateV1Map(map, objectMapper));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateV1Map_ok() {
        Map<String, Object> map = Map.of("a", "b");
        UserJsonBlobValidator.validateV1Map(map, objectMapper);
    }
}
