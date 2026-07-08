package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Workbook QueryType applicability for deterministic tool routing and evaluation coverage. */
public final class DeterministicToolApplicability {

    private static final Set<QueryType> APPLICABLE_TYPES =
            EnumSet.of(
                    QueryType.COUNT_DOCUMENTS,
                    QueryType.BOOLEAN_QUERY,
                    QueryType.GET_FIELD,
                    QueryType.FIND_PARAGRAPH,
                    QueryType.COUNT_AND_EXPLAIN,
                    QueryType.GET_DURATION,
                    QueryType.FILTER_AND_LIST,
                    QueryType.SUMMARIZE_MEETING,
                    QueryType.COMPARE);

    private DeterministicToolApplicability() {}

    public static boolean isApplicableQueryType(QueryType queryType) {
        return queryType != null && APPLICABLE_TYPES.contains(queryType);
    }

    public static Optional<DeterministicToolKind> toolKindForQueryType(QueryType queryType) {
        if (queryType == null) {
            return Optional.empty();
        }
        return switch (queryType) {
            case COUNT_DOCUMENTS -> Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
            case BOOLEAN_QUERY -> Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
            case GET_FIELD -> Optional.of(DeterministicToolKind.GET_FIELD_TOOL);
            case FIND_PARAGRAPH -> Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
            case COUNT_AND_EXPLAIN -> Optional.of(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL);
            case GET_DURATION -> Optional.of(DeterministicToolKind.GET_DURATION_TOOL);
            case FILTER_AND_LIST -> Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL);
            case SUMMARIZE_MEETING -> Optional.of(DeterministicToolKind.SUMMARIZE_MEETING_TOOL);
            case COMPARE -> Optional.of(DeterministicToolKind.COMPARE_TOOL);
            default -> Optional.empty();
        };
    }

    public static Set<QueryType> applicableTypes() {
        return Set.copyOf(APPLICABLE_TYPES);
    }
}
