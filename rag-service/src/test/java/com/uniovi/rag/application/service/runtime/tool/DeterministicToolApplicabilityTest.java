package com.uniovi.rag.application.service.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

class DeterministicToolApplicabilityTest {

    @Test
    void compareMapsToCompareTool() {
        assertThat(DeterministicToolApplicability.isApplicableQueryType(QueryType.COMPARE)).isTrue();
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.COMPARE))
                .contains(DeterministicToolKind.COMPARE_TOOL);
    }

    @Test
    void applicableQueryTypes_mapToToolKinds() {
        assertThat(DeterministicToolApplicability.isApplicableQueryType(QueryType.COUNT_DOCUMENTS)).isTrue();
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.COUNT_DOCUMENTS))
                .contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        assertThat(DeterministicToolApplicability.isApplicableQueryType(QueryType.SUMMARIZE_TOPIC)).isFalse();
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.COMPARE))
                .contains(DeterministicToolKind.COMPARE_TOOL);
    }

    @Test
    void applicableTypes_coversStructuredKinds() {
        assertThat(DeterministicToolApplicability.applicableTypes()).hasSize(9);
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.SUMMARIZE_MEETING))
                .contains(DeterministicToolKind.SUMMARIZE_MEETING_TOOL);
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.GET_DURATION))
                .contains(DeterministicToolKind.GET_DURATION_TOOL);
        assertThat(DeterministicToolApplicability.toolKindForQueryType(QueryType.FILTER_AND_LIST))
                .contains(DeterministicToolKind.FILTER_AND_LIST_TOOL);
    }
}
