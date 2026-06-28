package com.uniovi.rag.application.service.knowledge.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits Spanish community-meeting actas into section-aware chunks so participant lists,
 * agenda blocks, and closing lines are not torn across arbitrary character boundaries.
 */
public final class ActaSectionChunker {

    private ActaSectionChunker() {}

    public static boolean isActaContent(String content) {
        return MetadataMinuteDocumentService.looksLikeActaDocument(content);
    }

    public static List<ActaSectionChunk> chunk(String content, int maxCharsPerChunk) {
        if (content == null || content.isBlank()) {
            return List.of(new ActaSectionChunk("", ActaSectionChunk.SECTION_BODY, 0));
        }
        if (!isActaContent(content)) {
            return splitPlainText(content.trim(), maxCharsPerChunk, ActaSectionChunk.SECTION_BODY);
        }
        String trimmed = content.trim();
        int participantsStart = findParticipantsStart(trimmed);
        int agendaStart = findMarker(trimmed, "orden del día", "orden del dia");
        int closingStart = findMarker(trimmed, "no habiendo más", "no habiendo mas");

        List<SectionSlice> slices = new ArrayList<>();
        int headerEnd = participantsStart >= 0 ? participantsStart : agendaStart >= 0 ? agendaStart : trimmed.length();
        addSlice(slices, trimmed, 0, headerEnd, ActaSectionChunk.SECTION_HEADER);

        if (participantsStart >= 0) {
            int participantsEnd = agendaStart >= 0 ? agendaStart : closingStart >= 0 ? closingStart : trimmed.length();
            addSlice(slices, trimmed, participantsStart, participantsEnd, ActaSectionChunk.SECTION_PARTICIPANTS);
        }

        if (agendaStart >= 0) {
            int agendaEnd = closingStart >= 0 ? closingStart : trimmed.length();
            addSlice(slices, trimmed, agendaStart, agendaEnd, ActaSectionChunk.SECTION_AGENDA);
        } else if (participantsStart < 0 && headerEnd < trimmed.length()) {
            addSlice(slices, trimmed, headerEnd, trimmed.length(), ActaSectionChunk.SECTION_AGENDA);
        }

        if (closingStart >= 0) {
            addSlice(slices, trimmed, closingStart, trimmed.length(), ActaSectionChunk.SECTION_CLOSING);
        }

        if (slices.isEmpty()) {
            return splitPlainText(trimmed, maxCharsPerChunk, ActaSectionChunk.SECTION_BODY);
        }

        List<ActaSectionChunk> out = new ArrayList<>();
        for (SectionSlice slice : slices) {
            out.addAll(splitPlainText(slice.text(), maxCharsPerChunk, slice.sectionType()));
        }
        return out.isEmpty() ? List.of(new ActaSectionChunk(trimmed, ActaSectionChunk.SECTION_BODY, 0)) : out;
    }

    private static void addSlice(List<SectionSlice> slices, String content, int start, int end, String sectionType) {
        if (start < 0 || end <= start || start >= content.length()) {
            return;
        }
        int safeEnd = Math.min(end, content.length());
        String text = content.substring(start, safeEnd).trim();
        if (!text.isEmpty()) {
            slices.add(new SectionSlice(text, sectionType));
        }
    }

    private static List<ActaSectionChunk> splitPlainText(String text, int maxCharsPerChunk, String sectionType) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxCharsPerChunk) {
            return List.of(new ActaSectionChunk(trimmed, sectionType, 0));
        }
        List<ActaSectionChunk> chunks = new ArrayList<>();
        int start = 0;
        int part = 0;
        while (start < trimmed.length()) {
            int end = computeChunkEndIndex(trimmed, start, maxCharsPerChunk);
            String piece = trimmed.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new ActaSectionChunk(piece, sectionType, part++));
            }
            start = end;
        }
        return chunks;
    }

    private static int computeChunkEndIndex(String trimmed, int start, int maxCharsPerChunk) {
        int end = Math.min(start + maxCharsPerChunk, trimmed.length());
        if (end >= trimmed.length()) {
            return end;
        }
        int lastBreak = end;
        for (int i = end - 1; i > start + (maxCharsPerChunk * 2 / 3); i--) {
            char c = trimmed.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                lastBreak = i + 1;
                break;
            }
        }
        return lastBreak;
    }

    private static int findParticipantsStart(String content) {
        int asistentes = indexOfIgnoreCase(content, "Asistentes:");
        if (asistentes >= 0) {
            return asistentes;
        }
        int bulletPresident = indexOfIgnoreCase(content, "• ");
        if (bulletPresident >= 0) {
            return bulletPresident;
        }
        return indexOfIgnoreCase(content, "\n•");
    }

    private static int findAgendaStart(String content) {
        return findMarker(content, "orden del día", "orden del dia");
    }

    private static int findClosingStart(String content) {
        return findMarker(content, "no habiendo más", "no habiendo mas");
    }

    private static int findMarker(String content, String... markers) {
        int best = -1;
        for (String marker : markers) {
            int idx = indexOfIgnoreCase(content, marker);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static int indexOfIgnoreCase(String content, String needle) {
        if (content == null || needle == null || needle.isEmpty()) {
            return -1;
        }
        return content.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private record SectionSlice(String text, String sectionType) {}
}
