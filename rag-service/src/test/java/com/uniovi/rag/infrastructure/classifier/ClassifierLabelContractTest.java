package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassifierLabelContractTest {

    @Test
    void javaQueryTypeEnum_matchesClassifierServiceContract() {
        Set<String> javaLabels =
                Arrays.stream(QueryType.values()).map(Enum::name).collect(Collectors.toSet());
        assertThat(javaLabels)
                .containsExactlyInAnyOrder(
                        "COUNT_DOCUMENTS",
                        "EXTRACT_ENTITIES",
                        "COUNT_AND_EXPLAIN",
                        "FIND_PARAGRAPH",
                        "DECISION_EXTRACTION",
                        "GET_DURATION",
                        "GET_FIELD",
                        "SUMMARIZE_TOPIC",
                        "SUMMARIZE_MEETING",
                        "BOOLEAN_QUERY",
                        "FILTER_AND_LIST",
                        "COMPARE");
    }
}
