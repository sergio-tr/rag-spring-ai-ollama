package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Reserved-name guardrails for registered classifier models. */
class ModelRegistryDefaultReservedNameTest {

    @Test
    void cannotUseDefaultAsRegisteredModelName() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("default"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_RESERVED);
    }

    @Test
    void cannotUseDefaultAsUserInferenceTag() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidInferenceTag("default", false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(RegisteredModelValidation.INFERENCE_TAG_RESERVED);
    }

    @Test
    void rejectsNullAndNoneNames() {
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("none"))
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_RESERVED);
        assertThatThrownBy(() -> RegisteredModelValidation.assertValidName("NULL"))
                .hasMessageContaining(RegisteredModelValidation.MODEL_NAME_RESERVED);
    }
}
