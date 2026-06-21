package com.uniovi.rag.application.service.runtime.query;

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
                        || hasExplicitDateInText(q)
                        || q.contains("last") || q.contains("últim") || q.contains("next") || q.contains("próxim");
        if ((asksSummary || asksCompare) && !hasTemporal) {
            missing.add("time_reference");
            reasons.add("Missing temporal anchor for summary/compare");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        boolean asksPresident =
                q.contains("presidente") || q.contains("presidió") || q.contains("presidio") || q.contains("presidió");
        boolean asksWho = q.contains("quién") || q.contains("quien");
        if (asksPresident && asksWho && !hasTemporal && !q.contains("todas las actas") && !q.contains("cada acta")) {
            missing.add("time_reference");
            reasons.add("Missing date/meeting for president lookup");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        return AmbiguityAssessment.sufficient();
    }

    private static boolean hasExplicitDateInText(String q) {
        return q.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")
                || q.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b.*")
                || q.matches(".*\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}\\b.*")
                || q.matches(".*\\baño\\s+(del\\s+)?\\d{4}\\b.*")
                || q.matches(".*\\bdel\\s+año\\s+\\d{4}\\b.*");
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

