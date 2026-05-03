package com.uniovi.rag.application.service.account;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectVisualStyleValidatorTest {

    @Test
    void validateColorHexOrNull_acceptsNullAndBlank() {
        assertDoesNotThrow(() -> ProjectVisualStyleValidator.validateColorHexOrNull(null));
        assertDoesNotThrow(() -> ProjectVisualStyleValidator.validateColorHexOrNull("  "));
    }

    @Test
    void validateColorHexOrNull_invalidPattern_throwsBadRequest() {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ProjectVisualStyleValidator.validateColorHexOrNull("red"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateColorHexOrNull_validUppercaseHex_ok() {
        assertDoesNotThrow(() -> ProjectVisualStyleValidator.validateColorHexOrNull("#AABBCC"));
    }

    @Test
    void validateIconKeyOrNull_unknownKey_throwsBadRequest() {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ProjectVisualStyleValidator.validateIconKeyOrNull("unknown"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void validateIconKeyOrNull_catalogValue_ok() {
        assertDoesNotThrow(() -> ProjectVisualStyleValidator.validateIconKeyOrNull("folder"));
    }
}
