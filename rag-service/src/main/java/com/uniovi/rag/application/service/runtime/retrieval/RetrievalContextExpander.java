package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Expands retrieved chunks with acta section siblings and deduplicates per-document hits for count queries.
 */
@Service
public class RetrievalContextExpander {

    private static final int NEIGHBOR_RADIUS = 1;

    private final ActaChunkNeighborLoader neighborLoader;

    public RetrievalContextExpander(ActaChunkNeighborLoader neighborLoader) {
        this.neighborLoader = neighborLoader;
    }

    public record ExpansionResult(List<RetrievalCandidate> candidates, int dedupedDocumentCount, List<String> notes) {}

    public ExpansionResult expand(RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ExpansionResult(List.of(), 0, List.of());
        }
        List<String> notes = new ArrayList<>();
        List<RetrievalCandidate> working = new ArrayList<>(candidates);

        boolean scopedAttendeeCount = isScopedAttendeeCountQuery(req != null ? req.queryText() : null);
        if (shouldDedupeByDocument(plan) && !scopedAttendeeCount) {
            int before = working.size();
            working = dedupeByDocument(working);
            notes.add("count_dedupe:" + before + "->" + working.size());
        }

        if (shouldExpandContext(plan, req)) {
            int beforeExpand = working.size();
            working = expandActaSections(req, plan, working);
            if (working.size() != beforeExpand) {
                notes.add("section_expand:" + beforeExpand + "->" + working.size());
            }
        }

        // Keep highest scored context first, then apply the runtime post-fusion cap.
        working.sort(Comparator.comparingDouble(RetrievalCandidate::fusedRrfScore).reversed());
        int cap = req != null ? Math.max(0, req.postFusionCap()) : 0;
        if (cap > 0 && working.size() > cap) {
            int beforeCap = working.size();
            String query = req.queryText();
            if (scopedAttendeeCount || ActaSectionContextPolicy.needsParticipantsExpansion(query)) {
                working = applyCapPreservingRelevantSections(working, cap, query);
            } else if (ActaSectionContextPolicy.focus(query) != ActaSectionContextPolicy.SectionFocus.NONE) {
                working = applyCapPreservingRelevantSections(working, cap, query);
            } else {
                working = new ArrayList<>(working.subList(0, cap));
            }
            notes.add("post_fusion_cap:" + beforeCap + "->" + working.size());
        }

        return new ExpansionResult(List.copyOf(working), working.size(), List.copyOf(notes));
    }

    private static boolean shouldDedupeByDocument(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        return plan.queryIntent() == QueryIntent.COUNT
                || plan.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_COUNT;
    }

    private static boolean shouldExpandContext(QueryPlan plan, RetrievalRequest req) {
        if (plan == null) {
            return false;
        }
        if (isScopedAttendeeCountQuery(req != null ? req.queryText() : null)) {
            return true;
        }
        if (plan.queryIntent() == QueryIntent.COUNT
                || plan.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_COUNT) {
            return false;
        }
        return plan.queryIntent() == QueryIntent.LIST
                || plan.queryIntent() == QueryIntent.SUMMARIZE
                || plan.queryIntent() == QueryIntent.FIND
                || plan.expectedAnswerShape() == ExpectedAnswerShape.LIST
                || plan.expectedAnswerShape() == ExpectedAnswerShape.SUMMARY
                || plan.expectedAnswerShape() == ExpectedAnswerShape.PARAGRAPH
                || mentionsSectionSensitiveTopic(req != null ? req.queryText() : null);
    }

    private static boolean mentionsSectionSensitiveTopic(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("particip")
                || lower.contains("asistent")
                || lower.contains("propietarios")
                || lower.contains("orden del día")
                || lower.contains("orden del dia")
                || lower.contains("agenda")
                || lower.contains("president")
                || lower.contains("resum")
                || lower.contains("seccion")
                || lower.contains("sección")
                || lower.contains("duración")
                || lower.contains("duracion")
                || lower.contains("decisión")
                || lower.contains("decisiones")
                || lower.contains("acuerdo")
                || lower.contains("acuerdos")
                || lower.contains("cámara")
                || lower.contains("camara")
                || lower.contains("calefac")
                || lower.contains("evaluación")
                || lower.contains("evaluacion")
                || lower.contains("videovigilanc")
                || lower.contains("seguridad");
    }

    private List<RetrievalCandidate> expandActaSections(
            RetrievalRequest req, QueryPlan plan, List<RetrievalCandidate> candidates) {
        UUID projectId = req.projectId();
        if (projectId == null) {
            return mergeInBatchSectionSiblings(candidates);
        }

        Map<String, RetrievalCandidate> byId = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : candidates) {
            byId.putIfAbsent(candidate.candidateId(), candidate);
        }

        for (RetrievalCandidate seed : candidates) {
            Map<String, Object> meta = seed.metadata();
            String sectionType = stringOrNull(meta.get("sectionType"));
            String docId = documentKey(meta);
            UUID snapshotId = seed.snapshotId();
            if (docId == null || snapshotId == null) {
                continue;
            }

            List<RetrievalCandidate> siblings;
            if (sectionType != null) {
                siblings = neighborLoader.loadSectionSiblings(projectId, snapshotId, docId, sectionType, NEIGHBOR_RADIUS);
            } else {
                Integer chunkIndex = chunkIndex(meta);
                if (chunkIndex == null) {
                    continue;
                }
                siblings = neighborLoader.loadNeighborChunks(projectId, snapshotId, docId, chunkIndex, NEIGHBOR_RADIUS);
            }
            double seedScore = seed.fusedRrfScore();
            for (RetrievalCandidate sibling : siblings) {
                byId.putIfAbsent(sibling.candidateId(), withMinScore(sibling, seedScore));
            }

            String query = req != null ? req.queryText() : null;
            if (needsHeaderContext(plan, req)) {
                List<RetrievalCandidate> header =
                        neighborLoader.loadSectionSiblings(
                                projectId, snapshotId, docId, ActaSectionChunk.SECTION_HEADER, 0);
                for (RetrievalCandidate h : header) {
                    byId.putIfAbsent(h.candidateId(), withMinScore(h, seedScore));
                }
            }
            if (ActaSectionContextPolicy.needsParticipantsExpansion(query)
                    || isScopedAttendeeCountQuery(query)) {
                List<RetrievalCandidate> participants =
                        neighborLoader.loadSectionSiblings(
                                projectId, snapshotId, docId, ActaSectionChunk.SECTION_PARTICIPANTS, 0);
                for (RetrievalCandidate p : participants) {
                    byId.putIfAbsent(p.candidateId(), withMinScore(p, seedScore));
                }
            }
            if (ActaSectionContextPolicy.needsAgendaExpansion(query)) {
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_AGENDA, seedScore);
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_CLOSING, seedScore);
            }
            if (ActaSectionContextPolicy.needsSummaryExpansion(query)) {
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_AGENDA, seedScore);
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_BODY, seedScore);
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_CLOSING, seedScore);
            }
            if (ActaSectionContextPolicy.needsBodyTopicExpansion(query)) {
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_BODY, seedScore);
                addSectionSiblings(byId, projectId, snapshotId, docId, ActaSectionChunk.SECTION_AGENDA, seedScore);
            }
        }

        List<RetrievalCandidate> merged = mergeInBatchSectionSiblings(new ArrayList<>(byId.values()));
        return merged;
    }

    private void addSectionSiblings(
            Map<String, RetrievalCandidate> byId,
            UUID projectId,
            UUID snapshotId,
            String docId,
            String sectionType,
            double seedScore) {
        List<RetrievalCandidate> siblings =
                neighborLoader.loadSectionSiblings(projectId, snapshotId, docId, sectionType, 0);
        if (siblings == null || siblings.isEmpty()) {
            return;
        }
        for (RetrievalCandidate sibling : siblings) {
            byId.putIfAbsent(sibling.candidateId(), withMinScore(sibling, seedScore));
        }
    }

    private static boolean needsHeaderContext(QueryPlan plan, RetrievalRequest req) {
        if (plan != null && plan.queryIntent() == QueryIntent.SUMMARIZE) {
            return true;
        }
        String q = req != null ? req.queryText() : "";
        if (q == null) {
            return false;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (isScopedAttendeeCountQuery(q)) {
            return true;
        }
        if (ActaSectionContextPolicy.needsParticipantsExpansion(q)) {
            return true;
        }
        return lower.contains("resum") || lower.contains("duración") || lower.contains("duracion");
    }

    /** Attendee totals live in the acta header section; expand when the query is scoped to one acta. */
    static boolean isScopedAttendeeCountQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        boolean asksCount =
                (lower.contains("cuántos") || lower.contains("cuantos") || lower.contains("cuántas") || lower.contains("cuantas"))
                        && (lower.contains("propietarios")
                                || lower.contains("asistentes")
                                || lower.contains("participantes"));
        if (!asksCount) {
            return false;
        }
        return ActaFieldAnchorHeuristics.hasExplicitDateInText(lower)
                || ActaFieldAnchorHeuristics.hasExplicitActaDocumentReference(lower);
    }

    private static List<RetrievalCandidate> mergeInBatchSectionSiblings(List<RetrievalCandidate> candidates) {
        Map<String, List<RetrievalCandidate>> groups = new LinkedHashMap<>();
        for (RetrievalCandidate c : candidates) {
            String sectionType = stringOrNull(c.metadata().get("sectionType"));
            if (sectionType == null) {
                groups.computeIfAbsent("id:" + c.candidateId(), k -> new ArrayList<>()).add(c);
                continue;
            }
            String key = documentKey(c.metadata()) + "|" + sectionType;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        List<RetrievalCandidate> out = new ArrayList<>();
        for (List<RetrievalCandidate> group : groups.values()) {
            if (group.size() == 1) {
                out.add(group.getFirst());
            } else {
                out.add(mergeSectionGroup(group));
            }
        }
        return out;
    }

    private static RetrievalCandidate mergeSectionGroup(List<RetrievalCandidate> group) {
        List<RetrievalCandidate> sorted = new ArrayList<>(group);
        sorted.sort(
                Comparator.comparingInt((RetrievalCandidate c) -> Optional.ofNullable(chunkIndex(c.metadata())).orElse(0))
                        .thenComparing(RetrievalCandidate::candidateId));

        StringBuilder content = new StringBuilder();
        for (RetrievalCandidate c : sorted) {
            if (!content.isEmpty()) {
                content.append("\n");
            }
            content.append(c.content() != null ? c.content().trim() : "");
        }

        RetrievalCandidate best =
                sorted.stream()
                        .max(Comparator.comparingDouble(RetrievalCandidate::fusedRrfScore))
                        .orElse(sorted.getFirst());

        Map<String, Object> meta = new HashMap<>(best.metadata());
        meta.put("sectionExpanded", true);
        meta.put("mergedChunkCount", sorted.size());
        return new RetrievalCandidate(
                best.candidateId() + ":section",
                content.toString(),
                meta,
                best.denseScore(),
                best.sparseScore(),
                best.denseRank(),
                best.sparseRank(),
                best.snapshotId(),
                best.fusedRrfScore());
    }

    private static List<RetrievalCandidate> dedupeByDocument(List<RetrievalCandidate> candidates) {
        Map<String, RetrievalCandidate> bestByDoc = new LinkedHashMap<>();
        for (RetrievalCandidate c : candidates) {
            String docKey = documentKey(c.metadata());
            if (docKey == null) {
                docKey = c.candidateId();
            }
            bestByDoc.merge(
                    docKey,
                    c,
                    (a, b) -> a.fusedRrfScore() >= b.fusedRrfScore() ? a : b);
        }
        return new ArrayList<>(bestByDoc.values());
    }

    private static String documentKey(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        for (String key : List.of("projectDocumentId", "documentId", "document_id", "id")) {
            String value = stringOrNull(meta.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer chunkIndex(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        for (String key : List.of("chunkIndex", "chunk_index")) {
            Object raw = meta.get(key);
            if (raw instanceof Number n) {
                return n.intValue();
            }
            if (raw != null) {
                try {
                    return Integer.parseInt(raw.toString().trim());
                } catch (NumberFormatException ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static RetrievalCandidate withMinScore(RetrievalCandidate candidate, double minScore) {
        if (candidate.fusedRrfScore() >= minScore) {
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
                minScore);
    }

    /** Keep section-relevant evidence when post-fusion cap would drop it. */
    private static List<RetrievalCandidate> applyCapPreservingRelevantSections(
            List<RetrievalCandidate> candidates, int cap, String query) {
        List<RetrievalCandidate> pinned = new ArrayList<>();
        List<RetrievalCandidate> rest = new ArrayList<>();
        for (RetrievalCandidate c : candidates) {
            if (ActaSectionContextPolicy.shouldPinThroughCap(query, c) || isAttendeeSectionCandidate(c)) {
                pinned.add(c);
            } else {
                rest.add(c);
            }
        }
        List<RetrievalCandidate> out = new ArrayList<>(pinned);
        for (RetrievalCandidate c : rest) {
            if (out.size() >= cap) {
                break;
            }
            out.add(c);
        }
        if (out.size() > cap) {
            return new ArrayList<>(out.subList(0, cap));
        }
        return out;
    }

    private static boolean isAttendeeSectionCandidate(RetrievalCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String sectionType = stringOrNull(candidate.metadata().get("sectionType"));
        if (ActaSectionChunk.SECTION_PARTICIPANTS.equals(sectionType)
                || ActaSectionChunk.SECTION_HEADER.equals(sectionType)) {
            return true;
        }
        String content = candidate.content() != null ? candidate.content().toLowerCase(Locale.ROOT) : "";
        return content.contains("propietarios")
                || content.contains("asistentes")
                || content.contains("asistencia");
    }
}
