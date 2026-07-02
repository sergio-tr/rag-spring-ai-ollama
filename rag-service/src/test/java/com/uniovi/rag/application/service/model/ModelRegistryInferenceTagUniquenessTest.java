package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModelRegistryInferenceTagUniquenessTest {

    @Test
    void duplicateInferenceTagBlockedPerOwner() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertNoDuplicateInferenceTag(true))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.INFERENCE_TAG_DUPLICATE);
    }

    @Test
    void uniqueInferenceTagAllowed() {
        RegisteredModelValidation.assertNoDuplicateInferenceTag(false);
    }
}
