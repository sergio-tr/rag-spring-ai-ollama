package com.uniovi.rag.domain.config.capability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityGroupTest {

    @Test
    void forCapability_mapsEveryCapabilityToExactlyOneGroup() {
        assertThat(CapabilityGroup.forCapability(Capability.REASONING))
                .isEqualTo(CapabilityGroup.WORKFLOW_FAMILY);

        assertThat(CapabilityGroup.forCapability(Capability.EXPANSION))
                .isEqualTo(CapabilityGroup.QUERY_UNDERSTANDING);
        assertThat(CapabilityGroup.forCapability(Capability.NER))
                .isEqualTo(CapabilityGroup.QUERY_UNDERSTANDING);

        assertThat(CapabilityGroup.forCapability(Capability.USE_RETRIEVAL))
                .isEqualTo(CapabilityGroup.RETRIEVAL);
        assertThat(CapabilityGroup.forCapability(Capability.METADATA))
                .isEqualTo(CapabilityGroup.RETRIEVAL);
        assertThat(CapabilityGroup.forCapability(Capability.NAIVE_FULL_CORPUS_PROMPT))
                .isEqualTo(CapabilityGroup.RETRIEVAL);

        assertThat(CapabilityGroup.forCapability(Capability.POST_RETRIEVAL))
                .isEqualTo(CapabilityGroup.POST_RETRIEVAL);
        assertThat(CapabilityGroup.forCapability(Capability.RANKER))
                .isEqualTo(CapabilityGroup.POST_RETRIEVAL);

        assertThat(CapabilityGroup.forCapability(Capability.TOOLS))
                .isEqualTo(CapabilityGroup.TOOL_EXECUTION);
        assertThat(CapabilityGroup.forCapability(Capability.FUNCTION_CALLING))
                .isEqualTo(CapabilityGroup.TOOL_EXECUTION);

        assertThat(CapabilityGroup.forCapability(Capability.USE_ADVISOR))
                .isEqualTo(CapabilityGroup.ADVISOR);
    }
}

