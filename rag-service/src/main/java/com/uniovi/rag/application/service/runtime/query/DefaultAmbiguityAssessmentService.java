package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.application.service.runtime.optimization.DeterministicQueryRewriteShortcuts;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultAmbiguityAssessmentService implements AmbiguityAssessmentService {

    @Override
    public AmbiguityAssessment assess(
            NormalizedQuery normalized,
            Optional<QueryType> classifierQueryType,
            String classifierLabel,
            ClassifierStatus classifierStatus,
            StructuredRewriteResult rewrite,
            EntityExtractionResult entities) {

        Optional<QueryType> cqt = Objects.requireNonNullElseGet(classifierQueryType, Optional::empty);
        List<String> reasons = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        String qLower = normalized.normalizedText().toLowerCase(Locale.ROOT);

        Optional<IncompleteQueryHeuristics.Signal> incomplete =
                IncompleteQueryHeuristics.detect(normalized.normalizedText());
        if (incomplete.isPresent()) {
            return IncompleteQueryHeuristics.toAmbiguityAssessment(incomplete.get());
        }

        if (ActaFieldAnchorHeuristics.isCorpusWideAggregate(qLower)) {
            return AmbiguityAssessment.sufficient();
        }
        if (DeterministicQueryRewriteShortcuts.matches(normalized.normalizedText()).isPresent()) {
            return AmbiguityAssessment.sufficient();
        }
        if (ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(qLower)) {
            return AmbiguityAssessment.sufficient();
        }
        if (ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(qLower)) {
            return AmbiguityAssessment.sufficient();
        }
        if (ActaFieldAnchorHeuristics.isDatedSummaryRequest(qLower)) {
            return AmbiguityAssessment.sufficient();
        }

        // Missing acta/date anchor takes priority over classifier/rewrite conflict (FD-CL-01).
        if (ActaFieldAnchorHeuristics.needsActaAnchor(normalized.normalizedText(), entities)) {
            missing.add("time_reference");
            reasons.add("Missing acta/meeting date for scoped field lookup");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        // memory-expanded planning input may carry ISO dates from structured anchors.
        String effectiveLower = normalized.normalizedText().toLowerCase(Locale.ROOT);
        boolean asksPresident =
                effectiveLower.contains("presidente")
                        || effectiveLower.contains("presidió")
                        || effectiveLower.contains("presidio");
        boolean asksWho = effectiveLower.contains("quién") || effectiveLower.contains("quien");
        if (asksPresident
                && asksWho
                && ActaFieldAnchorHeuristics.hasExplicitDateInText(effectiveLower)
                && !ActaFieldAnchorHeuristics.isCorpusWideAggregate(effectiveLower)) {
            return AmbiguityAssessment.sufficient();
        }

        // Conflict signal: classifier (when OK) vs rewrite targetAction (when present)
        Optional<String> targetAction = rewrite != null ? rewrite.targetAction() : Optional.empty();
        if (classifierStatus == ClassifierStatus.OK && cqt.isPresent()
                && targetAction.isPresent()) {
            String action = targetAction.get().trim().toUpperCase(Locale.ROOT);
            QueryType classifierType = cqt.get();
            if (!action.isBlank() && !classifierCompatibleWithRewriteAction(classifierType, action)) {
                reasons.add(
                        "CONFLICT: classifier="
                                + classifierType.name()
                                + " rewriteAction="
                                + action);
                return new AmbiguityAssessment(AmbiguityStatus.CONFLICTING_CUES, reasons, List.of());
            }
        }

        // Missing info: summarize/compare without any temporal anchor (heuristic, non-blocking)
        String q = normalized.normalizedText().toLowerCase(Locale.ROOT);
        boolean asksSummary = q.contains("summar") || q.contains("resum");
        boolean asksCompare = q.contains("compare") || q.contains("compar");
        boolean hasTemporal =
                (entities != null && !entities.dates().isEmpty())
                        || ActaFieldAnchorHeuristics.hasExplicitDateInText(q)
                        || q.contains("last") || q.contains("últim") || q.contains("next") || q.contains("próxim");
        if ((asksSummary || asksCompare) && !hasTemporal && !ActaFieldAnchorHeuristics.isCorpusWideAggregate(q)) {
            missing.add("time_reference");
            reasons.add("Missing temporal anchor for summary/compare");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        boolean asksPresidentLate =
                q.contains("presidente") || q.contains("presidió") || q.contains("presidio");
        boolean asksWhoLate = q.contains("quién") || q.contains("quien");
        if (asksPresidentLate && asksWhoLate && !hasTemporal && !ActaFieldAnchorHeuristics.isCorpusWideAggregate(q)) {
            missing.add("time_reference");
            reasons.add("Missing date/meeting for president lookup");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        return AmbiguityAssessment.sufficient();
    }

    private static boolean classifierCompatibleWithRewriteAction(QueryType classifierType, String action) {
        String classifier = classifierType.name().toUpperCase(Locale.ROOT);
        if (classifier.contains(action) || action.contains(classifier)) {
            return true;
        }
        return switch (classifierType) {
            case GET_FIELD -> "EXTRACT_FIELD".equals(action) || "LIST".equals(action) || "FIND".equals(action);
            case COUNT_DOCUMENTS -> "COUNT".equals(action);
            case FILTER_AND_LIST -> "LIST".equals(action);
            case FIND_PARAGRAPH, GET_DURATION, DECISION_EXTRACTION -> "FIND".equals(action);
            case BOOLEAN_QUERY -> "BOOLEAN_CHECK".equals(action);
            case SUMMARIZE_MEETING, SUMMARIZE_TOPIC -> "SUMMARIZE".equals(action);
            case COUNT_AND_EXPLAIN -> "EXPLAIN".equals(action) || "COUNT".equals(action);
            case COMPARE -> "COMPARE".equals(action);
            case EXTRACT_ENTITIES -> "EXTRACT_FIELD".equals(action) || "EXPLAIN".equals(action);
        };
    }
}

