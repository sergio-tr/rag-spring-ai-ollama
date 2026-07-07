package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.document.ActaSectionChunk;
import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps acta question intent to required section types for expansion, reranking, and compression protection.
 */
final class ActaSectionContextPolicy {

    private static final double W_SECTION_MATCH = 150.0;
    private static final double W_HEADER_PENALTY = -180.0;

    private ActaSectionContextPolicy() {}

    enum SectionFocus {
        NONE,
        PARTICIPANTS,
        DECISIONS,
        TOPICS,
        SUMMARY
    }

    static SectionFocus focus(String query) {
        if (query == null || query.isBlank()) {
            return SectionFocus.NONE;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (needsParticipantsExpansion(lower)) {
            return SectionFocus.PARTICIPANTS;
        }
        if (needsAgendaExpansion(lower)) {
            return SectionFocus.DECISIONS;
        }
        if (needsSummaryExpansion(lower)) {
            return SectionFocus.SUMMARY;
        }
        if (needsBodyTopicExpansion(lower)) {
            return SectionFocus.TOPICS;
        }
        return SectionFocus.NONE;
    }

    static boolean needsParticipantsExpansion(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if (ActaFieldAnchorHeuristics.isCorpusWideAggregate(lower)) {
            return false;
        }
        boolean asksParticipants =
                lower.contains("asistent")
                        || lower.contains("particip")
                        || lower.contains("propietarios")
                        || lower.contains("asistencia");
        if (!asksParticipants) {
            return false;
        }
        boolean scoped =
                ActaFieldAnchorHeuristics.hasExplicitDateInText(lower)
                        || ActaFieldAnchorHeuristics.hasExplicitActaDocumentReference(lower);
        return scoped;
    }

    static boolean needsAgendaExpansion(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("decisión")
                || lower.contains("decisiones")
                || lower.contains("acuerdo")
                || lower.contains("acuerdos");
    }

    static boolean needsSummaryExpansion(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        boolean asksSummary = lower.contains("resume") || lower.contains("resum");
        boolean asksPoints =
                lower.contains("puntos tratados")
                        || lower.contains("puntos del día")
                        || lower.contains("puntos del dia")
                        || lower.contains("temas tratados");
        return asksSummary && (asksPoints || ActaFieldAnchorHeuristics.hasExplicitActaDocumentReference(lower));
    }

    static boolean needsBodyTopicExpansion(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        boolean asksTopicContent =
                lower.contains("qué se habla")
                        || lower.contains("que se habla")
                        || lower.contains("en qué actas")
                        || lower.contains("en que actas")
                        || lower.contains("habla sobre")
                        || lower.contains("hablan sobre")
                        || lower.contains("mencion");
        boolean hasTopicKeyword =
                lower.contains("cámara")
                        || lower.contains("camara")
                        || lower.contains("videovigilanc")
                        || lower.contains("seguridad")
                        || lower.contains("calefac")
                        || lower.contains("climatiz")
                        || lower.contains("evaluación")
                        || lower.contains("evaluacion");
        return asksTopicContent || hasTopicKeyword;
    }

    static double sectionRerankAdjustment(String query, RetrievalCandidate candidate) {
        SectionFocus focus = focus(query);
        if (focus == SectionFocus.NONE || candidate == null) {
            return 0.0;
        }
        String sectionType = sectionType(candidate.metadata());
        String content = candidate.content() != null ? candidate.content().toLowerCase(Locale.ROOT) : "";
        double adjustment = 0.0;

        switch (focus) {
            case PARTICIPANTS -> {
                if (ActaSectionChunk.SECTION_PARTICIPANTS.equals(sectionType)) {
                    adjustment += W_SECTION_MATCH;
                } else if (ActaSectionChunk.SECTION_HEADER.equals(sectionType) && content.contains("asistent")) {
                    adjustment += W_SECTION_MATCH * 0.35;
                } else if (isHeaderOnly(candidate) && !contentContainsParticipantEvidence(content)) {
                    adjustment += W_HEADER_PENALTY;
                }
            }
            case DECISIONS, SUMMARY -> {
                if (ActaSectionChunk.SECTION_AGENDA.equals(sectionType)
                        || ActaSectionChunk.SECTION_AGREEMENTS.equals(sectionType)
                        || ActaSectionChunk.SECTION_BODY.equals(sectionType)) {
                    adjustment += W_SECTION_MATCH;
                } else if (contentContainsDecisionEvidence(content)) {
                    adjustment += W_SECTION_MATCH * 0.6;
                } else if (isHeaderOnly(candidate)) {
                    adjustment += W_HEADER_PENALTY;
                }
            }
            case TOPICS -> {
                if (ActaSectionChunk.SECTION_AGENDA.equals(sectionType)
                        || ActaSectionChunk.SECTION_BODY.equals(sectionType)) {
                    adjustment += W_SECTION_MATCH;
                } else if (queryTopicTokensPresent(query, content)) {
                    adjustment += W_SECTION_MATCH * 0.5;
                } else if (isHeaderOnly(candidate) && !queryTopicTokensPresent(query, content)) {
                    adjustment += W_HEADER_PENALTY;
                }
            }
            default -> {}
        }
        return adjustment;
    }

    static boolean isProtectedFromCompression(String query, RetrievalCandidate candidate) {
        SectionFocus focus = focus(query);
        if (focus == SectionFocus.NONE || candidate == null) {
            return false;
        }
        String sectionType = sectionType(candidate.metadata());
        String content = candidate.content() != null ? candidate.content().toLowerCase(Locale.ROOT) : "";
        return switch (focus) {
            case PARTICIPANTS ->
                    ActaSectionChunk.SECTION_PARTICIPANTS.equals(sectionType)
                            || (ActaSectionChunk.SECTION_HEADER.equals(sectionType)
                                    && contentContainsParticipantEvidence(content));
            case DECISIONS, SUMMARY ->
                    ActaSectionChunk.SECTION_AGENDA.equals(sectionType)
                            || ActaSectionChunk.SECTION_AGREEMENTS.equals(sectionType)
                            || ActaSectionChunk.SECTION_BODY.equals(sectionType)
                            || contentContainsDecisionEvidence(content);
            case TOPICS ->
                    ActaSectionChunk.SECTION_AGENDA.equals(sectionType)
                            || ActaSectionChunk.SECTION_BODY.equals(sectionType)
                            || queryTopicTokensPresent(query, content);
            default -> false;
        };
    }

    static boolean shouldPinThroughCap(String query, RetrievalCandidate candidate) {
        return isProtectedFromCompression(query, candidate);
    }

    private static boolean isHeaderOnly(RetrievalCandidate candidate) {
        String sectionType = sectionType(candidate.metadata());
        if (ActaSectionChunk.SECTION_HEADER.equals(sectionType)) {
            return true;
        }
        Integer idx = chunkIndex(candidate.metadata());
        return idx != null && idx == 0 && !ActaSectionChunk.SECTION_PARTICIPANTS.equals(sectionType);
    }

    private static boolean contentContainsParticipantEvidence(String content) {
        return content.contains("asistent")
                || content.contains("propietario")
                || content.contains("asistencia")
                || content.contains("• ");
    }

    private static boolean contentContainsDecisionEvidence(String content) {
        return content.contains("decisión")
                || content.contains("decisiones")
                || content.contains("acuerdo")
                || content.contains("acuerdos")
                || content.contains("se aprueba")
                || content.contains("se acord")
                || content.contains("punto ");
    }

    private static boolean queryTopicTokensPresent(String query, String content) {
        if (query == null || content == null || content.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        if ((lower.contains("cámara") || lower.contains("camara") || lower.contains("seguridad"))
                && (content.contains("cámara")
                        || content.contains("camara")
                        || content.contains("videovigilanc")
                        || content.contains("seguridad"))) {
            return true;
        }
        if (lower.contains("calefac") || lower.contains("evaluación") || lower.contains("evaluacion")) {
            return content.contains("calefac")
                    || content.contains("climatiz")
                    || content.contains("evaluación")
                    || content.contains("evaluacion");
        }
        return false;
    }

    private static String sectionType(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object raw = meta.get("sectionType");
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer chunkIndex(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        for (String key : List.of("chunkIndex", "chunk_index")) {
            Object raw = meta.get(key);
            if (raw instanceof Number number) {
                return number.intValue();
            }
        }
        return null;
    }
}
