package com.uniovi.rag.configuration;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression: FC whitelist {@link DeterministicToolKind} stays aligned with {@link ToolDescriptor} / {@code @Tool} names.
 */
class DeterministicToolDescriptorAlignmentTest {

    @Test
    void everyDeterministicKindHasNonBlankDescriptor() {
        for (DeterministicToolKind k : DeterministicToolKind.values()) {
            var qt = k.toQueryType();
            assertFalse(ToolDescriptor.getName(qt).isBlank(), () -> "name for " + k);
            assertFalse(ToolDescriptor.getDescription(qt).isBlank(), () -> "description for " + k);
        }
    }
}
