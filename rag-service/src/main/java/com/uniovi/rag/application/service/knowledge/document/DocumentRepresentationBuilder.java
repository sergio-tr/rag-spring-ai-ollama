package com.uniovi.rag.application.service.knowledge.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the single whole-document text embedded for a {@code DOCUMENT_LEVEL} vector or a
 * {@code HYBRID} doc-tail vector. Unlike a plain first-N-chars cut, the body is a balanced
 * head/middle/tail sample of the raw text, and an optional bounded ACTA metadata summary is
 * prefixed when structured metadata is available and enabled. Never used for {@code CHUNK_LEVEL}.
 */
public final class DocumentRepresentationBuilder {

    private DocumentRepresentationBuilder() {}

    public record Representation(String text, boolean truncated) {}

    private static final double HEAD_RATIO = 0.45;
    private static final double TAIL_RATIO = 0.45;
    private static final String SEGMENT_SEPARATOR = " (...) ";
    private static final double METADATA_BUDGET_RATIO = 0.55;
    private static final int MIN_BODY_BUDGET = 200;
    private static final int DEGENERATE_BODY_BUDGET = 20;
    private static final int MAX_DECISION_CUES = 3;
    private static final int BOUNDARY_SEARCH_FLOOR = 24;

    private static final Pattern DECISION_CUE_SENTENCE =
            Pattern.compile(
                    "(?i)[^.!?\\n]*\\b(se acuerda|se aprueba|se decide|queda aprobad[oa])\\b[^.!?\\n]*[.!?]");

    /**
     * @param rawContent full extracted document text
     * @param filename display filename, used only in the metadata block
     * @param structuredActa deterministic ACTA fields, or null/empty when unavailable
     * @param metadataEnabled whether the caller's index profile has metadata enabled
     * @param maxChars hard ceiling for the returned text (whole-document embed ceiling)
     */
    public static Representation build(
            String rawContent,
            String filename,
            Map<String, Object> structuredActa,
            boolean metadataEnabled,
            int maxChars) {
        String content = rawContent == null ? "" : rawContent.trim();
        int safeMax = Math.max(1, maxChars);
        boolean hasMetadata = metadataEnabled && structuredActa != null && !structuredActa.isEmpty();

        if (!hasMetadata) {
            BodyResult body = buildBalancedBody(content, safeMax);
            return new Representation(body.text(), body.truncated());
        }

        List<String> decisionCues = extractDecisionCues(content, MAX_DECISION_CUES);
        int minBody = Math.min(MIN_BODY_BUDGET, Math.max(1, safeMax / 2));
        int metadataBudget = Math.max(0, Math.min((int) Math.floor(safeMax * METADATA_BUDGET_RATIO), safeMax - minBody));
        MetadataResult metadataResult = buildMetadataBlock(structuredActa, filename, decisionCues, metadataBudget);

        int bodyBudget = Math.max(1, safeMax - metadataResult.text().length() - (metadataResult.text().isEmpty() ? 0 : 1));
        BodyResult body = buildBalancedBody(content, bodyBudget);

        String combined =
                metadataResult.text().isEmpty() ? body.text() : metadataResult.text() + "\n" + body.text();
        boolean truncated = body.truncated() || metadataResult.truncated();
        if (combined.length() > safeMax) {
            combined = hardTrim(combined, safeMax);
            truncated = true;
        }
        return new Representation(combined, truncated);
    }

    // ---- balanced head/middle/tail body ----

    private record BodyResult(String text, boolean truncated) {}

    private record Slice(String text, int start, int end) {}

    private static BodyResult buildBalancedBody(String text, int budget) {
        if (text.isEmpty()) {
            return new BodyResult("", false);
        }
        if (text.length() <= budget) {
            return new BodyResult(text, false);
        }
        if (budget <= DEGENERATE_BODY_BUDGET) {
            Slice head = sliceHead(text, budget);
            return new BodyResult(head.text(), true);
        }

        int sepLen = SEGMENT_SEPARATOR.length();
        int headLen = (int) Math.floor(budget * HEAD_RATIO);
        int tailLen = (int) Math.floor(budget * TAIL_RATIO);

        Slice head = sliceHead(text, headLen);
        Slice tail = sliceTail(text, tailLen);
        if (tail.start() <= head.end()) {
            // Overlap guard for short-ish documents: fall back to head+tail only, no middle sample.
            tail = sliceTail(text, Math.max(0, text.length() - head.end()));
        }

        int gapStart = head.end();
        int gapEnd = Math.max(gapStart, tail.start());
        int gapSize = gapEnd - gapStart;
        int middleBudget = Math.max(0, budget - head.text().length() - tail.text().length() - 2 * sepLen);
        int middleLen = Math.min(middleBudget, gapSize);

        Slice middle = middleLen > sepLen ? sliceMiddle(text, gapStart + (gapSize - middleLen) / 2, middleLen) : new Slice("", gapStart, gapStart);

        StringBuilder sb = new StringBuilder();
        sb.append(head.text());
        if (!middle.text().isBlank()) {
            sb.append(SEGMENT_SEPARATOR).append(middle.text());
        }
        if (!tail.text().isBlank()) {
            sb.append(SEGMENT_SEPARATOR).append(tail.text());
        }
        String result = sb.toString().trim();
        if (result.length() > budget) {
            result = hardTrim(result, budget);
        }
        return new BodyResult(result, true);
    }

    private static Slice sliceHead(String text, int approxLen) {
        int end = Math.min(Math.max(0, approxLen), text.length());
        int snapped = snapBackward(text, end);
        return new Slice(text.substring(0, snapped).trim(), 0, snapped);
    }

    private static Slice sliceTail(String text, int approxLen) {
        int start = Math.max(0, text.length() - Math.max(0, approxLen));
        int snapped = snapForward(text, start);
        return new Slice(text.substring(snapped).trim(), snapped, text.length());
    }

    private static Slice sliceMiddle(String text, int approxStart, int len) {
        int start = Math.max(0, Math.min(approxStart, text.length()));
        int end = Math.min(start + Math.max(0, len), text.length());
        int snappedStart = snapForward(text, start);
        int snappedEnd = snapBackward(text, end);
        if (snappedEnd <= snappedStart) {
            return new Slice("", start, start);
        }
        return new Slice(text.substring(snappedStart, snappedEnd).trim(), snappedStart, snappedEnd);
    }

    /** Nearest break at or before {@code idealEnd} (word/sentence boundary), never past the text end. */
    private static int snapBackward(String text, int idealEnd) {
        int end = Math.min(idealEnd, text.length());
        if (end >= text.length() || end <= 0) {
            return end;
        }
        int window = Math.max(BOUNDARY_SEARCH_FLOOR, end / 4);
        int searchFrom = Math.max(0, end - window);
        for (int i = end - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                return i + 1;
            }
        }
        return end;
    }

    /** Nearest break at or after {@code idealStart}, so a tail/middle slice never starts mid-word. */
    private static int snapForward(String text, int idealStart) {
        int start = Math.max(0, Math.min(idealStart, text.length()));
        if (start <= 0 || start >= text.length()) {
            return start;
        }
        int window = Math.max(BOUNDARY_SEARCH_FLOOR, (text.length() - start) / 4);
        int searchTo = Math.min(text.length(), start + window);
        for (int i = start; i < searchTo; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == ' ') {
                return Math.min(text.length(), i + 1);
            }
        }
        return start;
    }

    // ---- bounded ACTA metadata block ----

    private record MetadataResult(String text, boolean truncated) {}

    private static MetadataResult buildMetadataBlock(
            Map<String, Object> acta, String filename, List<String> decisionCues, int budget) {
        if (budget <= 0) {
            return new MetadataResult("", true);
        }
        boolean[] truncatedHolder = {false};

        String header = buildHeaderLine(acta);
        String filenameLine = (filename != null && !filename.isBlank()) ? "Archivo: " + filename + "." : null;
        int reservedForShortLines = (header != null ? header.length() + 1 : 0) + (filenameLine != null ? filenameLine.length() + 1 : 0);
        int listBudget = Math.max(0, budget - reservedForShortLines);

        List<String> attendees = asStringList(acta.get("attendees"));
        int attendeesCount = asInt(acta.get("numberOfAttendees"), attendees.size());
        int attendeesBudget = (int) Math.floor(listBudget * 0.65);
        ListLine attendeesLine =
                buildListLine(
                        attendeesCount > 0 ? "Asistentes (" + attendeesCount + "): " : "Asistentes: ",
                        attendees,
                        ", ",
                        attendeesBudget);
        truncatedHolder[0] |= attendeesLine.truncated();

        int remainingForTopics = Math.max(0, listBudget - attendeesLine.text().length());
        List<String> topics = asStringList(acta.get("topics"));
        int topicsBudget = (int) Math.floor(remainingForTopics * 0.7);
        ListLine topicsLine = buildListLine("Orden del día: ", topics, "; ", topicsBudget);
        truncatedHolder[0] |= topicsLine.truncated();

        int remainingForDecisions = Math.max(0, remainingForTopics - topicsLine.text().length());
        ListLine decisionsLine = buildListLine("Decisiones: ", decisionCues, " ", remainingForDecisions);

        List<String> lines = new ArrayList<>();
        if (header != null) {
            lines.add(header);
        }
        if (!attendeesLine.text().isEmpty()) {
            lines.add(attendeesLine.text());
        }
        if (!topicsLine.text().isEmpty()) {
            lines.add(topicsLine.text());
        }
        if (!decisionsLine.text().isEmpty()) {
            lines.add(decisionsLine.text());
        }
        if (filenameLine != null) {
            lines.add(filenameLine);
        }

        String combined = String.join(" ", lines).trim();
        if (combined.length() > budget) {
            combined = hardTrim(combined, budget);
            truncatedHolder[0] = true;
        }
        return new MetadataResult(combined, truncatedHolder[0]);
    }

    private static String buildHeaderLine(Map<String, Object> acta) {
        String date = firstNonBlank(str(acta.get("date_iso")), str(acta.get("date")));
        String president = str(acta.get("president"));
        String secretary = str(acta.get("secretary"));
        if (date.isBlank() && president.isBlank() && secretary.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (!date.isBlank()) {
            sb.append("Acta ").append(date).append(".");
        }
        if (!president.isBlank()) {
            sb.append(sb.isEmpty() ? "" : " ").append("Presidente: ").append(president).append(".");
        }
        if (!secretary.isBlank()) {
            sb.append(sb.isEmpty() ? "" : " ").append("Secretario: ").append(secretary).append(".");
        }
        return sb.toString();
    }

    private record ListLine(String text, boolean truncated) {}

    /**
     * Joins {@code items} under {@code label} within {@code budget}. Includes as many whole items
     * as fit (never cuts an item mid-string); appends a "(+N more)" marker when some had to be
     * dropped, so long lists are only shortened at an item boundary, never mid-list.
     */
    private static ListLine buildListLine(String label, List<String> items, String separator, int budget) {
        if (items.isEmpty() || budget <= 0) {
            return new ListLine("", false);
        }
        String full = label + String.join(separator, items) + ".";
        if (full.length() <= budget) {
            return new ListLine(full, false);
        }
        List<String> included = new ArrayList<>();
        int used = label.length();
        for (String item : items) {
            int addition = (included.isEmpty() ? 0 : separator.length()) + item.length();
            String suffixIfStoppingHere = " (+" + (items.size() - included.size()) + " more).";
            if (used + addition + suffixIfStoppingHere.length() > budget) {
                break;
            }
            used += addition;
            included.add(item);
        }
        if (included.isEmpty()) {
            return new ListLine("", true);
        }
        int omitted = items.size() - included.size();
        String result =
                label
                        + String.join(separator, included)
                        + (omitted > 0 ? " (+" + omitted + " more)." : ".");
        return new ListLine(result, omitted > 0);
    }

    // ---- decision cues (lightweight, independent of the deterministic `decisions` field) ----

    private static List<String> extractDecisionCues(String content, int max) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> cues = new ArrayList<>();
        Matcher matcher = DECISION_CUE_SENTENCE.matcher(content);
        while (matcher.find() && cues.size() < max) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                cues.add(sentence);
            }
        }
        return cues;
    }

    // ---- small helpers ----

    private static String hardTrim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max)).trim();
    }

    private static String str(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private static String firstNonBlank(String a, String b) {
        return !a.isBlank() ? a : b;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !o.toString().isBlank()) {
                out.add(o.toString().trim());
            }
        }
        return out;
    }
}
