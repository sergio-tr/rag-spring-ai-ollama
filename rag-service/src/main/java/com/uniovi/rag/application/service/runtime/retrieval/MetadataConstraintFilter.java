package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.MetadataFilterTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** High-confidence metadata constraint filtering with empty-set fallback for advanced retrieval. */
@Component
public class MetadataConstraintFilter {

    public record FilterResult(List<RetrievalCandidate> candidates, MetadataFilterTelemetry telemetry) {}

    public FilterResult apply(RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new FilterResult(List.of(), new MetadataFilterTelemetry(false, false));
        }
        if (!shouldApplyHardFilter(req, plan)) {
            return new FilterResult(candidates, new MetadataFilterTelemetry(false, false));
        }

        List<String> requiredTokens = requiredMatchTokens(req, plan);
        if (requiredTokens.isEmpty()) {
            return new FilterResult(candidates, new MetadataFilterTelemetry(false, false));
        }

        List<RetrievalCandidate> filtered = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            if (matchesAllTokens(c, requiredTokens)) {
                filtered.add(c);
            }
        }
        if (filtered.isEmpty()) {
            return new FilterResult(candidates, new MetadataFilterTelemetry(true, true));
        }
        return new FilterResult(filtered, new MetadataFilterTelemetry(true, false));
    }

    private static boolean shouldApplyHardFilter(RetrievalRequest req, QueryPlan plan) {
        if (plan == null || plan.entityExtractionResult() == null) {
            return false;
        }
        boolean hasDate = !plan.entityExtractionResult().dates().isEmpty();
        boolean hasPerson = !plan.entityExtractionResult().people().isEmpty();
        if (!hasDate && !hasPerson) {
            return false;
        }
        String q = req.queryText() != null ? req.queryText().toLowerCase(Locale.ROOT) : "";
        if (hasPerson && (q.contains("confirma") || q.contains("verifica") || q.contains("aparece"))) {
            return true;
        }
        if (hasDate && (q.contains("duración") || q.contains("duracion") || q.contains("duró") || q.contains("duro"))) {
            return true;
        }
        return false;
    }

    private static List<String> requiredMatchTokens(RetrievalRequest req, QueryPlan plan) {
        List<String> out = new ArrayList<>();
        if (plan.entityExtractionResult() != null) {
            for (String d : plan.entityExtractionResult().dates()) {
                if (d != null && !d.isBlank()) {
                    out.add(d.trim());
                }
            }
            for (String p : plan.entityExtractionResult().people()) {
                if (p != null && !p.isBlank()) {
                    out.add(p.trim());
                }
            }
        }
        return out;
    }

    private static boolean matchesAllTokens(RetrievalCandidate c, List<String> tokens) {
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (metadataMatchesToken(c.metadata(), token) || contentMatchesToken(c, token)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean metadataMatchesToken(Map<String, Object> metadata, String token) {
        if (metadata == null || token == null || token.isBlank()) {
            return false;
        }
        if (RetrievalEntityMatchingSupport.metadataContainsDateToken(metadata, token)) {
            return true;
        }
        return RetrievalEntityMatchingSupport.metadataContainsPerson(metadata, token);
    }

    private static boolean contentMatchesToken(RetrievalCandidate c, String token) {
        String content = c.content() != null ? c.content() : "";
        String filename =
                c.metadata() != null && c.metadata().get("filename") != null
                        ? String.valueOf(c.metadata().get("filename"))
                        : "";
        String hay = content + "\n" + filename;
        return RetrievalEntityMatchingSupport.containsEntityToken(hay, token);
    }
}
