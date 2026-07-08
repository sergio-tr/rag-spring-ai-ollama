package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModelRegistryDuplicateNameTest {

    @Test
    void duplicateNameBlocked() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertNoDuplicateName(true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_DUPLICATE);
    }

    @Test
    void uniqueNameAllowed() {
        RegisteredModelValidation.assertNoDuplicateName(false);
    }
}
