package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.application.service.runtime.query.ActaDocumentAnchorSupport;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Re-ranks retrieval candidates using indexed acta metadata when the metadata workflow is active.
 */
final class MetadataRetrievalBooster {

    private static final double TOPIC_MATCH_BOOST = 0.06;
    private static final double SECTION_MATCH_BOOST = 0.08;
    private static final double PARTICIPANTS_LIST_BOOST = 0.15;
    private static final double FILENAME_MATCH_BOOST = 0.12;

    private MetadataRetrievalBooster() {}

    static List<RetrievalCandidate> apply(
            RetrievalRequest req, QueryPlan plan, String workflowName, List<RetrievalCandidate> candidates) {
        if (!AdvancedRetrievalPipeline.WORKFLOW_CHUNK_DENSE_METADATA.equals(workflowName)
                || candidates == null
                || candidates.isEmpty()) {
            return candidates != null ? candidates : List.of();
        }
        String query = req != null && req.queryText() != null ? req.queryText().toLowerCase(Locale.ROOT) : "";
        List<RetrievalCandidate> boosted = new ArrayList<>(candidates.size());
        for (RetrievalCandidate candidate : candidates) {
            boosted.add(boostCandidate(candidate, query, plan));
        }
        boosted.sort(Comparator.comparingDouble(RetrievalCandidate::fusedRrfScore).reversed());
        return boosted;
    }

    private static RetrievalCandidate boostCandidate(
            RetrievalCandidate candidate, String query, QueryPlan plan) {
        Map<String, Object> meta = candidate.metadata();
        double boost = 0.0;
        boost += topicOverlapBoost(query, meta);
        boost += sectionRelevanceBoost(query, plan, meta);
        boost += filenameAnchorBoost(query, meta);
        if (boost <= 0.0) {
            return candidate;
        }
        return new RetrievalCandidate(
                candidate.candidateId(),
                candidate.content(),
                candidate.metadata(),
                candidate.denseScore(),
                candidate.sparseScore(),
                candidate.denseRank(),
                candidate.sparseRank(),
                candidate.snapshotId(),
                candidate.fusedRrfScore() + boost);
    }

    private static double topicOverlapBoost(String query, Map<String, Object> meta) {
        Object topicsObj = meta != null ? meta.get("topics") : null;
        if (!(topicsObj instanceof List<?> topics) || topics.isEmpty() || query.isBlank()) {
            return 0.0;
        }
        for (Object topic : topics) {
            if (topic == null) {
                continue;
            }
            String normalized = topic.toString().toLowerCase(Locale.ROOT).trim();
            if (normalized.length() >= 4 && query.contains(normalized)) {
                return TOPIC_MATCH_BOOST;
            }
            for (String token : normalized.split("\\s+")) {
                if (token.length() >= 5 && query.contains(token)) {
                    return TOPIC_MATCH_BOOST;
                }
            }
        }
        return 0.0;
    }

    private static double sectionRelevanceBoost(String query, QueryPlan plan, Map<String, Object> meta) {
        String sectionType = stringOrNull(meta != null ? meta.get("sectionType") : null);
        if (sectionType == null) {
            return 0.0;
        }
        if (mentionsAttendees(query) && ActaSectionChunk.SECTION_PARTICIPANTS.equals(sectionType)) {
            return RetrievalContextExpander.isScopedAttendeeCountQuery(query)
                    ? SECTION_MATCH_BOOST * 0.35
                    : PARTICIPANTS_LIST_BOOST;
        }
        if (mentionsAttendees(query)
                && RetrievalContextExpander.isScopedAttendeeCountQuery(query)
                && ActaSectionChunk.SECTION_HEADER.equals(sectionType)) {
            return SECTION_MATCH_BOOST * 0.35;
        }
        if (mentionsDecisions(query)
                && (ActaSectionChunk.SECTION_CLOSING.equals(sectionType)
                        || ActaSectionChunk.SECTION_BODY.equals(sectionType))) {
            return SECTION_MATCH_BOOST;
        }
        if (plan != null
                && (plan.queryIntent() == QueryIntent.SUMMARIZE || query.contains("agenda"))
                && ActaSectionChunk.SECTION_AGENDA.equals(sectionType)) {
            return SECTION_MATCH_BOOST;
        }
        return 0.0;
    }

    private static double filenameAnchorBoost(String query, Map<String, Object> meta) {
        if (meta == null || query.isBlank()) {
            return 0.0;
        }
        String filename = stringOrNull(meta.get("filename"));
        if (filename == null) {
            return 0.0;
        }
        var actaNum = ActaDocumentAnchorSupport.resolveActaNumber(query);
        if (actaNum.isPresent()
                && filename.toLowerCase(Locale.ROOT).contains("acta " + actaNum.get())) {
            return FILENAME_MATCH_BOOST;
        }
        return 0.0;
    }

    private static boolean mentionsAttendees(String query) {
        return query.contains("asistent")
                || query.contains("particip")
                || query.contains("propietarios");
    }

    private static boolean mentionsDecisions(String query) {
        return query.contains("decision") || query.contains("acuerdo");
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = Objects.toString(value, "").trim();
        return s.isEmpty() ? null : s;
    }
}
