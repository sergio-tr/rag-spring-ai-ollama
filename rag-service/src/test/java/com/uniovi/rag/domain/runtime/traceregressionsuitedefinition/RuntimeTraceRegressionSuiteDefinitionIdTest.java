package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTraceRegressionSuiteDefinitionIdTest {

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new RuntimeTraceRegressionSuiteDefinitionId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definition id");
    }

    @Test
    void acceptsUuid() {
        UUID id = UUID.randomUUID();
        assertThat(new RuntimeTraceRegressionSuiteDefinitionId(id).value()).isEqualTo(id);
    }
}
