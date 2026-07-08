package com.uniovi.rag.application.service.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative Markdown normalization for user-visible assistant answers: repairs common LLM list
 * formatting issues without changing factual content.
 */
public final class MarkdownAnswerFormatter {

    private static final Pattern INLINE_COLON_BULLET =
            Pattern.compile("(:)\\s*([*\\-])\\s+");
    private static final Pattern MERGED_LIST_AND_PROSE =
            Pattern.compile(
                    "^(.+?)\\s+((?:The|According|En|Según|In|La\\s|El\\s|Los\\s|Las\\s).+)$",
                    Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    private static final Pattern INLINE_SOURCE_ON_LINE =
            Pattern.compile(
                    "^(.+?)\\s+((?:Source|Fuentes?(?:\\s+consultadas)?)\\s*:.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    private static final Pattern SOURCE_ONLY_LINE =
            Pattern.compile(
                    "^(?:Source|Fuentes?(?:\\s+consultadas)?)\\s*:.+$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    private static final Pattern PARAGRAPH_START =
            Pattern.compile(
                    "^(?:The|According|En|Según|In|La\\s|El\\s|Los\\s|Las\\s|Based|Se\\s+encontr)",
                    Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[*\\-]\\s+(.*)$");

    private MarkdownAnswerFormatter() {}

    public static String format(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<Segment> segments = splitProtectedSegments(normalized);
        StringBuilder rebuilt = new StringBuilder();
        for (Segment segment : segments) {
            if (!rebuilt.isEmpty()) {
                rebuilt.append('\n');
            }
            rebuilt.append(segment.protectedBlock() ? segment.content() : formatProse(segment.content()));
        }
        return normalizeListSpacing(rebuilt.toString()).trim();
    }

    private static String formatProse(String text) {
        String withColonBullets = INLINE_COLON_BULLET.matcher(text).replaceAll("$1\n\n- ");
        return repairListLines(withColonBullets);
    }

    private static String repairListLines(String text) {
        String[] lines = text.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean inList = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                    inList = false;
                    out.add("");
                } else if (!inList) {
                    out.add("");
                }
                continue;
            }

            if (isTableLine(line)) {
                inList = false;
                out.add(line);
                continue;
            }

            List<String> expanded = expandMergedLine(line, inList);
            for (int i = 0; i < expanded.size(); i++) {
                String segment = expanded.get(i).trim();
                if (segment.isEmpty()) {
                    continue;
                }
                if (SOURCE_ONLY_LINE.matcher(segment).matches()) {
                    inList = false;
                    ensureBlankLine(out);
                    out.add(segment);
                    continue;
                }
                Matcher bullet = BULLET_PREFIX.matcher(segment);
                if (bullet.matches()) {
                    inList = true;
                    out.add("- " + bullet.group(1).trim());
                    continue;
                }
                if (inList && looksLikeListItem(segment)) {
                    out.add("- " + segment);
                    continue;
                }
                if (PARAGRAPH_START.matcher(segment).find()) {
                    inList = false;
                    ensureBlankLine(out);
                    out.add(segment);
                    continue;
                }
                inList = false;
                out.add(segment);
            }
        }
        return String.join("\n", out);
    }

    private static List<String> expandMergedLine(String line, boolean inList) {
        List<String> parts = new ArrayList<>();
        String current = line;

        Matcher source = INLINE_SOURCE_ON_LINE.matcher(current);
        if (source.matches()) {
            String before = source.group(1).trim();
            String footer = source.group(2).trim();
            if (!before.isEmpty()) {
                parts.addAll(expandMergedLine(before, inList));
            }
            parts.add(footer);
            return parts;
        }

        Matcher merged = MERGED_LIST_AND_PROSE.matcher(current);
        if ((inList || BULLET_PREFIX.matcher(current).matches() || looksLikeListItem(current))
                && merged.matches()) {
            parts.add(merged.group(1).trim());
            parts.add(merged.group(2).trim());
            return parts;
        }

        parts.add(current);
        return parts;
    }

    private static boolean looksLikeListItem(String line) {
        if (line.isBlank() || SOURCE_ONLY_LINE.matcher(line).matches()) {
            return false;
        }
        // Multi-sentence prose (e.g. "Una. La acta 1.pdf...") must not be split as list + paragraph.
        if (line.matches(".*\\.\\s+\\p{Lu}.*")) {
            return false;
        }
        if (PARAGRAPH_START.matcher(line).find()) {
            return false;
        }
        if (line.length() > 160) {
            return false;
        }
        if (line.endsWith(".") && line.contains(" ")) {
            int lastPeriod = line.lastIndexOf('.');
            if (lastPeriod < line.length() - 1) {
                return false;
            }
            String afterPeriod = line.substring(lastPeriod + 1).trim();
            if (!afterPeriod.isEmpty()) {
                return false;
            }
        }
        return !line.matches(".*\\s(?:Source|Fuentes?)\\s*:.*");
    }

    private static void ensureBlankLine(List<String> out) {
        if (out.isEmpty()) {
            return;
        }
        if (!out.get(out.size() - 1).isEmpty()) {
            out.add("");
        }
    }

    private static String normalizeListSpacing(String text) {
        String[] lines = text.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean previousWasBullet = false;

        for (String rawLine : lines) {
            String line = rawLine;
            boolean isBullet = line.startsWith("- ") || line.startsWith("* ");
            if (isBullet && !previousWasBullet && !out.isEmpty() && !out.get(out.size() - 1).isEmpty()) {
                out.add("");
            }
            if (!isBullet && previousWasBullet && !line.isBlank() && !SOURCE_ONLY_LINE.matcher(line).matches()) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty()) {
                    out.add("");
                }
            }
            if (SOURCE_ONLY_LINE.matcher(line).matches() && !out.isEmpty() && !out.get(out.size() - 1).isEmpty()) {
                out.add("");
            }
            out.add(line);
            previousWasBullet = isBullet;
        }
        return String.join("\n", out);
    }

    private static boolean isTableLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.indexOf('|', 1) >= 0;
    }

    private static List<Segment> splitProtectedSegments(String text) {
        List<Segment> segments = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder current = new StringBuilder();
        boolean inFence = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inFence) {
                    current.append(line).append('\n');
                    segments.add(new Segment(current.toString(), true));
                    current = new StringBuilder();
                    inFence = false;
                } else {
                    if (!current.isEmpty()) {
                        segments.add(new Segment(current.toString(), false));
                        current = new StringBuilder();
                    }
                    current.append(line).append('\n');
                    inFence = true;
                }
                continue;
            }
            if (inFence) {
                current.append(line).append('\n');
                continue;
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            segments.add(new Segment(current.toString(), inFence));
        }
        return segments;
    }

    static String collapseHorizontalWhitespacePreservingNewlines(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String[] lines = text.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i].replaceAll("[ \\t]{2,}", " ").stripTrailing());
        }
        return out.toString();
    }

    private record Segment(String content, boolean protectedBlock) {}
}
