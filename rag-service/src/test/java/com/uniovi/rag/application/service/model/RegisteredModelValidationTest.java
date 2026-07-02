package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RegisteredModelValidationTest {

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_REQUIRED);
    }

    @Test
    void rejectsReservedNameDefault() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("default"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_RESERVED);
    }

    @Test
    void rejectsReservedNameCaseInsensitive() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("DEFAULT"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_RESERVED);
    }

    @Test
    void acceptsValidName() {
        RegisteredModelValidation.assertValidName(" acta-classifier-v1 ");
        assertThat(RegisteredModelValidation.normalizeName(" acta-classifier-v1 ")).isEqualTo("acta-classifier-v1");
    }

    @Test
    void rejectsReservedInferenceTagForUserModels() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidInferenceTag("default", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.INFERENCE_TAG_RESERVED);
    }

    @Test
    void allowsSystemDefaultInferenceTagWhenFlagged() {
        RegisteredModelValidation.assertValidInferenceTag("default", true);
    }

    @Test
    void duplicateNameThrowsConflict() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertNoDuplicateName(true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_DUPLICATE);
    }

    @Test
    void duplicateInferenceTagThrowsConflict() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertNoDuplicateInferenceTag(true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.INFERENCE_TAG_DUPLICATE);
    }
}
