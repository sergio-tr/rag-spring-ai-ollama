package com.uniovi.rag.domain.runtime.traceregressionsuitedefinition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTraceRegressionSuiteDefinitionValidationTest {

    @Test
    void blankNameAfterTrim_throws() {
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.normalizeAndValidateName("  \t "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameTooLong_throws() {
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.normalizeAndValidateName("x".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionTooLong_throws() {
        assertThatThrownBy(
                        () ->
                                RuntimeTraceRegressionSuiteDefinitionValidation.normalizeDescription(
                                        Optional.of("y".repeat(2049))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankDescriptionBecomesEmptyOptional() {
        assertThat(RuntimeTraceRegressionSuiteDefinitionValidation.normalizeDescription(Optional.of("  "))).isEmpty();
    }

    @Test
    void entryCountZero_throws() {
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entryCountTwentyOne_throws() {
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            entries.add(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(UUID.randomUUID())));
        }
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byTraceIds_fiftyOneTraces_throws() {
        List<UUID> ids = IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList();
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries =
                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(ids));
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateTraceIdInEntry_throws() {
        UUID id = UUID.randomUUID();
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries =
                List.of(new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByTraceIds(List.of(id, id)));
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byConversationWithoutConversationId_throws() {
        List<RuntimeTraceRegressionSuiteDefinitionEntrySpec> entries =
                List.of(
                        new RuntimeTraceRegressionSuiteDefinitionEntrySpec.ByConversation(
                                null, Optional.empty(), Optional.empty(), Optional.empty()));
        assertThatThrownBy(() -> RuntimeTraceRegressionSuiteDefinitionValidation.validateEntryList(entries))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
