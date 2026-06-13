package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

class DeterministicToolApplicabilityTest {

    @Test
    void applicableQueryTypes_mapToToolKinds() {
        assertThat(DeterministicToolApplicability.isApplicableQueryType(QueryType.COUNT_DOCUMENTS)).isTrue();
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.COUNT_DOCUMENTS))
                .contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        assertThat(DeterministicToolApplicability.isApplicableQueryType(QueryType.SUMMARIZE_TOPIC)).isFalse();
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.COMPARE)).isEmpty();
    }

    @Test
    void applicableTypes_coversFiveStructuredKinds() {
        assertThat(DeterministicToolApplicability.applicableTypes()).hasSize(5);
    }
}
