package com.uniovi.rag.domain.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record QueryPlan(
        String queryPlanVersion,
        String rawUserQuery,
        /** Input text passed to QU normalize for this turn (merged on continuation when pending clarification exists). */
        String effectivePlanningInputText,
        String normalizedQueryText,
        String rewrittenQueryText,
        String classifierLabel,
        Optional<QueryType> classifierQueryType,
        ClassifierStatus classifierStatus,
        QueryIntent queryIntent,
        Map<String, String> slots,
        List<String> targetEntities,
        List<String> targetAttributes,
        EntityExtractionResult entityExtractionResult,
        StructuredRewriteResult structuredRewriteResult,
        ExpectedAnswerShape expectedAnswerShape,
        AmbiguityAssessment ambiguityAssessment,
        String correlationId,
        String classifierModelIdUsed,
        List<String> pipelineNotes) {

    public static final String VERSION_P6_QU_CORE_V1 = "P6_QU_CORE_V1";
    public static final String VERSION_P11_QU_CLARIFICATION_CORE_V1 = "P11_QU_CLARIFICATION_CORE_V1";
    public static final String VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1 = "P12_MEMORY_CONVERSATIONAL_FLOW_V1";

    public QueryPlan {
        queryPlanVersion = requireSupportedQueryPlanVersion(queryPlanVersion);
        Objects.requireNonNull(rawUserQuery, "rawUserQuery");
        Objects.requireNonNull(effectivePlanningInputText, "effectivePlanningInputText");
        Objects.requireNonNull(normalizedQueryText, "normalizedQueryText");
        Objects.requireNonNull(rewrittenQueryText, "rewrittenQueryText");
        Objects.requireNonNull(classifierLabel, "classifierLabel");
        classifierQueryType = Objects.requireNonNullElseGet(classifierQueryType, Optional::empty);
        Objects.requireNonNull(classifierStatus, "classifierStatus");
        Objects.requireNonNull(queryIntent, "queryIntent");
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        targetEntities = List.copyOf(Objects.requireNonNull(targetEntities, "targetEntities"));
        targetAttributes = List.copyOf(Objects.requireNonNull(targetAttributes, "targetAttributes"));
        Objects.requireNonNull(entityExtractionResult, "entityExtractionResult");
        Objects.requireNonNull(structuredRewriteResult, "structuredRewriteResult");
        Objects.requireNonNull(expectedAnswerShape, "expectedAnswerShape");
        Objects.requireNonNull(ambiguityAssessment, "ambiguityAssessment");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(classifierModelIdUsed, "classifierModelIdUsed");
        pipelineNotes = List.copyOf(Objects.requireNonNull(pipelineNotes, "pipelineNotes"));
    }

    private static String requireSupportedQueryPlanVersion(String queryPlanVersion) {
        String v = Objects.requireNonNull(queryPlanVersion, "queryPlanVersion");
        if (VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1.equals(v)
                || VERSION_P11_QU_CLARIFICATION_CORE_V1.equals(v)
                || VERSION_P6_QU_CORE_V1.equals(v)) {
            return v;
        }
        throw new IllegalArgumentException("Unsupported queryPlanVersion: " + v);
    }
}

