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
        queryPlanVersion = Objects.requireNonNull(queryPlanVersion, "queryPlanVersion");
        rawUserQuery = Objects.requireNonNull(rawUserQuery, "rawUserQuery");
        effectivePlanningInputText =
                Objects.requireNonNull(effectivePlanningInputText, "effectivePlanningInputText");
        normalizedQueryText = Objects.requireNonNull(normalizedQueryText, "normalizedQueryText");
        rewrittenQueryText = Objects.requireNonNull(rewrittenQueryText, "rewrittenQueryText");
        classifierLabel = Objects.requireNonNull(classifierLabel, "classifierLabel");
        classifierQueryType = classifierQueryType == null ? Optional.empty() : classifierQueryType;
        classifierStatus = Objects.requireNonNull(classifierStatus, "classifierStatus");
        queryIntent = Objects.requireNonNull(queryIntent, "queryIntent");
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        targetEntities = List.copyOf(Objects.requireNonNull(targetEntities, "targetEntities"));
        targetAttributes = List.copyOf(Objects.requireNonNull(targetAttributes, "targetAttributes"));
        entityExtractionResult = Objects.requireNonNull(entityExtractionResult, "entityExtractionResult");
        structuredRewriteResult = Objects.requireNonNull(structuredRewriteResult, "structuredRewriteResult");
        expectedAnswerShape = Objects.requireNonNull(expectedAnswerShape, "expectedAnswerShape");
        ambiguityAssessment = Objects.requireNonNull(ambiguityAssessment, "ambiguityAssessment");
        correlationId = Objects.requireNonNull(correlationId, "correlationId");
        classifierModelIdUsed = Objects.requireNonNull(classifierModelIdUsed, "classifierModelIdUsed");
        pipelineNotes = List.copyOf(Objects.requireNonNull(pipelineNotes, "pipelineNotes"));

        if (!VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1.equals(queryPlanVersion)
                && !VERSION_P11_QU_CLARIFICATION_CORE_V1.equals(queryPlanVersion)
                && !VERSION_P6_QU_CORE_V1.equals(queryPlanVersion)) {
            throw new IllegalArgumentException("Unsupported queryPlanVersion: " + queryPlanVersion);
        }
    }
}

