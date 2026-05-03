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
            String classifier = cqt.orElseThrow().name().toUpperCase(Locale.ROOT);
            if (!action.isBlank() && !classifier.isBlank() && !classifier.contains(action)) {
                reasons.add("CONFLICT: classifier=" + classifier + " rewriteAction=" + action);
                return new AmbiguityAssessment(AmbiguityStatus.CONFLICTING_CUES, reasons, List.of());
            }
        }

        // Missing info: summarize/compare without any temporal anchor (heuristic, non-blocking)
        String q = normalized.normalizedText().toLowerCase(Locale.ROOT);
        boolean asksSummary = q.contains("summar") || q.contains("resum");
        boolean asksCompare = q.contains("compare") || q.contains("compar");
        boolean hasTemporal =
                (entities != null && (!entities.dates().isEmpty() || entities.temporalContext().isPresent()))
                        || q.contains("last") || q.contains("últim") || q.contains("next") || q.contains("próxim");
        if ((asksSummary || asksCompare) && !hasTemporal) {
            missing.add("time_reference");
            reasons.add("Missing temporal anchor for summary/compare");
            return new AmbiguityAssessment(AmbiguityStatus.MISSING_INFORMATION, reasons, missing);
        }

        return AmbiguityAssessment.sufficient();
    }
}

