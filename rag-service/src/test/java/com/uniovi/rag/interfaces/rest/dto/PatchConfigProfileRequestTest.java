package com.uniovi.rag.interfaces.rest.dto;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatchConfigProfileRequestTest {

    @Test
    void nonNullFieldNames_emptyWhenBothNull() {
        assertThat(new PatchConfigProfileRequest(null, null).nonNullFieldNames()).isEmpty();
    }

    @Test
    void nonNullFieldNames_includesLabel() {
        assertThat(new PatchConfigProfileRequest("L", null).nonNullFieldNames()).containsExactly("label");
    }

    @Test
    void nonNullFieldNames_includesPayload() {
        assertThat(new PatchConfigProfileRequest(null, Map.of("k", 1)).nonNullFieldNames())
                .containsExactly("payload");
    }

    @Test
    void nonNullFieldNames_ordersLabelThenPayload() {
        assertThat(new PatchConfigProfileRequest("L", Map.of()).nonNullFieldNames())
                .containsExactly("label", "payload");
    }
}
