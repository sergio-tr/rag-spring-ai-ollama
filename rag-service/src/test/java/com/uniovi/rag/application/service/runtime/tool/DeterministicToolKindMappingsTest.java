package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.configuration.ToolDescriptor;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicToolKindMappingsTest {

    @Test
    void fromDeclaredToolName_acceptsDescriptorNames() {
        for (DeterministicToolKind k : DeterministicToolKind.values()) {
            String declared = ToolDescriptor.getName(k.toQueryType());
            assertThat(DeterministicToolKindMappings.fromDeclaredToolName(declared)).contains(k);
        }
    }

    @Test
    void fromDeclaredToolName_acceptsHistoricalEnumConstantNames() {
        assertThat(DeterministicToolKindMappings.fromDeclaredToolName("COUNT_DOCUMENTS_TOOL"))
                .contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
    }
}
