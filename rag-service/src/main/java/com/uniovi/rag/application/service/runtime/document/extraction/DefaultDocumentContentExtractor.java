package com.uniovi.rag.application.service.runtime.document.extraction;

import com.uniovi.rag.domain.model.Cluster;
import com.uniovi.rag.util.RegexSafety;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of document/minute content extraction.
 * Consolidates logic previously in utils for clearer ownership and testability.
 */
public class DefaultDocumentContentExtractor implements DocumentContentExtractor {

    private static final int CASE_INSENSITIVE_UNICODE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    @Override
    public String extractDate(String content) {
        if (content == null) {
            return "Unknown date";
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("Fecha:\\s*(\\d{1,2} de [a-záéíóú]+ de \\d{4})", CASE_INSENSITIVE_UNICODE).matcher(c);
        return matcher.find() ? matcher.group(1) : "Unknown date";
    }

    @Override
    public String extractRelevantFragment(String content, String query) {
        if (query == null || query.isBlank()) return "";
        if (content == null) {
            return "";
        }
        String boundedContent = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        String boundedQuery = RegexSafety.truncateString(query, RegexSafety.MAX_QUERY_TEXT_FOR_REGEX);
        String cleanedQuery = boundedQuery.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").toLowerCase();
        String[] keywords = extractSignificantKeywords(cleanedQuery).toArray(new String[0]);
        String contentLower = boundedContent.toLowerCase();
        int bestMatchIdx = indexOfBestKeywordMatch(contentLower, keywords);
        if (bestMatchIdx == -1) {
            return boundedContent.length() > 300 ? boundedContent.substring(0, 300) + "..." : boundedContent;
        }
        int start = Math.max(0, bestMatchIdx - 80);
        int end = Math.min(boundedContent.length(), bestMatchIdx + 300);
        String snippet = boundedContent.substring(start, end).strip();
        if (!snippet.endsWith(".") && end < boundedContent.length()) {
            int nextDot = boundedContent.indexOf('.', end);
            if (nextDot != -1) {
                snippet += boundedContent.substring(end, Math.min(nextDot + 1, boundedContent.length()));
            }
        }
        return snippet + (end < boundedContent.length() ? "..." : "");
    }

    @Override
    public String extractTime(String content, String type) {
        if (content == null) {
            return null;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Pattern pattern = type.equals("start")
                ? Pattern.compile(
                        "(?i)hora de inicio:\\s*(\\d{1,2})\\s*:\\s*(\\d{2})\\s*h?",
                        CASE_INSENSITIVE_UNICODE)
                : Pattern.compile(
                        "(?i)(?:hora de finalización|hora de fin):\\s*(\\d{1,2})\\s*:\\s*(\\d{2})\\s*h?",
                        CASE_INSENSITIVE_UNICODE);
        Matcher matcher = pattern.matcher(c);
        if (!matcher.find()) {
            return null;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return String.format("%02d:%02d", hour, minute);
    }

    @Override
    public int extractAttendeeCount(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(\\d{1,2})\\s+propietarios", CASE_INSENSITIVE_UNICODE).matcher(c);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    @Override
    public int calculateDuration(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher startMatcher = Pattern.compile("Hora de inicio:\\s*(\\d{1,2}):(\\d{2})", CASE_INSENSITIVE_UNICODE).matcher(c);
        Matcher endMatcher = Pattern.compile("Hora de finalización.*?(\\d{1,2}):(\\d{2})", CASE_INSENSITIVE_UNICODE).matcher(c);
        if (startMatcher.find() && endMatcher.find()) {
            int start = Integer.parseInt(startMatcher.group(1)) * 60 + Integer.parseInt(startMatcher.group(2));
            int end = Integer.parseInt(endMatcher.group(1)) * 60 + Integer.parseInt(endMatcher.group(2));
            return end - start;
        }
        return 0;
    }

    @Override
    public String extractLiteralField(String field, String content) {
        if (content == null) {
            return null;
        }
        switch (field) {
            case "place":
                return match(content, "(?i)Lugar:\\s*(.+)");
            case "date":
                return extractDate(content);
            case "startTime":
                return extractTime(content, "start");
            case "endTime":
                return extractTime(content, "end");
            case "president":
                return extractAttendeesWithRoles(content).stream()
                        .filter(a -> "PRESIDENTE".equals(a.role()))
                        .map(Attendee::name)
                        .findFirst()
                        .orElse(null);
            case "secretary":
                return extractAttendeesWithRoles(content).stream()
                        .filter(a -> "SECRETARIO".equals(a.role()) || "SECRETARIA".equals(a.role()))
                        .map(Attendee::name)
                        .findFirst()
                        .orElse(null);
            default:
                return null;
        }
    }

    @Override
    public List<String> extractAttendees(String content) {
        List<String> attendees = new ArrayList<>();
        if (content == null) {
            return attendees;
        }
        for (Attendee a : extractAttendeesWithRoles(content)) {
            attendees.add(a.name());
        }
        return attendees;
    }

    private record Attendee(String name, String role) {
    }

    private List<Attendee> extractAttendeesWithRoles(String content) {
        if (content == null) {
            return List.of();
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        String section = attendeesSection(c);
        List<Attendee> out = new ArrayList<>();
        String pendingName = null;
        for (String rawLine : section.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("•")) {
                if (pendingName != null) {
                    out.add(new Attendee(pendingName, ""));
                }
                ParsedBullet bullet = parseBullet(line);
                if (!bullet.role().isBlank()) {
                    out.add(new Attendee(bullet.name(), bullet.role()));
                    pendingName = null;
                } else {
                    pendingName = bullet.name();
                }
                continue;
            }
            if (pendingName != null && isStandaloneRoleLine(line)) {
                out.add(new Attendee(pendingName, normalizeRole(unwrapRoleLine(line))));
                pendingName = null;
            }
        }
        if (pendingName != null) {
            out.add(new Attendee(pendingName, ""));
        }
        return out;
    }

    private static String attendeesSection(String content) {
        Matcher header = Pattern.compile("(?i)asistentes\\s*:").matcher(content);
        int start = header.find() ? header.start() : -1;
        if (start < 0) {
            start = content.toLowerCase(Locale.ROOT).indexOf("lista de asistencia");
        }
        if (start < 0) {
            return content;
        }
        int end = content.length();
        String lower = content.toLowerCase(Locale.ROOT);
        for (String marker :
                List.of("orden del día", "orden del dia", "puntos del día", "puntos del dia", "ruegos y preguntas")) {
            int idx = lower.indexOf(marker, start + 1);
            if (idx > start) {
                end = Math.min(end, idx);
            }
        }
        return content.substring(start, end);
    }

    private record ParsedBullet(String name, String role) {}

    private static ParsedBullet parseBullet(String bulletLine) {
        String tail = bulletLine.substring(1).strip();
        if (tail.isEmpty()) {
            return new ParsedBullet("", "");
        }
        int openIdx = tail.lastIndexOf('(');
        int closeIdx = tail.endsWith(")") ? tail.length() - 1 : -1;
        if (openIdx != -1 && closeIdx != -1 && openIdx < closeIdx) {
            String roleRaw = tail.substring(openIdx + 1, closeIdx).strip();
            String candidateName = tail.substring(0, openIdx).strip();
            if (!candidateName.isEmpty()) {
                return new ParsedBullet(candidateName, normalizeRole(roleRaw));
            }
        }
        return new ParsedBullet(tail, "");
    }

    private static boolean isStandaloneRoleLine(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("(") && trimmed.endsWith(")");
    }

    private static String unwrapRoleLine(String line) {
        String trimmed = line.strip();
        if (trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).strip();
        }
        return trimmed;
    }

    private static String normalizeRole(String raw) {
        if (raw == null) {
            return "";
        }
        String r = raw.strip().toUpperCase();
        if (r.startsWith("PRESIDENTE")) return "PRESIDENTE";
        if (r.startsWith("SECRETARIO")) return "SECRETARIO";
        if (r.startsWith("SECRETARIA")) return "SECRETARIA";
        return r;
    }

    @Override
    public String extractAgenda(String content) {
        if (content == null) {
            return null;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher =
                Pattern.compile(
                                "(?i)Orden del d[íi]a:\\s*(.+?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)",
                                Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS)
                        .matcher(c);
        if (matcher.find()) {
            return matcher.group(1).trim().replace("•", "-").replaceAll("\\n+", "\n- ");
        }
        return null;
    }

    @Override
    public int countProposals(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher =
                Pattern.compile("(?i)(propuesta|se plantea|se acuerda|se discute)", Pattern.UNICODE_CHARACTER_CLASS)
                        .matcher(c);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    @Override
    public int countAgendaItems(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher =
                Pattern.compile(
                                "(?i)Orden del d[íi]a:\\s*(.+?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)",
                                Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS)
                        .matcher(c);
        if (matcher.find()) {
            String agenda = matcher.group(1);
            return (int) agenda.lines().filter(line -> line.strip().startsWith("•") || line.strip().startsWith("-")).count();
        }
        return 0;
    }

    @Override
    public int countQuestions(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher =
                Pattern.compile("(?i)Ruegos y preguntas", Pattern.UNICODE_CHARACTER_CLASS).matcher(c);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    @Override
    public boolean containsAnyKeyword(String text, String[] keywords) {
        if (text == null || keywords == null) return false;
        String lower = text.toLowerCase();
        return Arrays.stream(keywords).map(String::toLowerCase).anyMatch(lower::contains);
    }

    @Override
    public <T> List<Cluster<T>> clusterItems(List<T> items,
                                             Function<T, String> contentExtractor,
                                             Function<T, String> typeExtractor,
                                             double similarityThreshold) {
        List<Cluster<T>> clusters = new ArrayList<>();
        for (T item : items) {
            boolean addedToCluster = false;
            for (Cluster<T> cluster : clusters) {
                if (isSimilarToCluster(item, cluster, contentExtractor, typeExtractor, similarityThreshold)) {
                    cluster.addItem(item);
                    addedToCluster = true;
                    break;
                }
            }
            if (!addedToCluster) {
                clusters.add(new Cluster<>(item));
            }
        }
        return clusters;
    }

    private static int keywordHitsAt(String contentLower, int index, String[] keywords) {
        int hits = 0;
        for (String word : keywords) {
            if (contentLower.startsWith(word, index)) {
                hits++;
            }
        }
        return hits;
    }

    private static int indexOfBestKeywordMatch(String contentLower, String[] keywords) {
        int bestMatchIdx = -1;
        int maxHits = 0;
        for (int i = 0; i < contentLower.length(); i++) {
            int hits = keywordHitsAt(contentLower, i, keywords);
            if (hits > maxHits) {
                maxHits = hits;
                bestMatchIdx = i;
            }
        }
        return bestMatchIdx;
    }

    private static String match(String content, String regex) {
        if (content == null) {
            return null;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile(regex).matcher(c);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static List<String> extractSignificantKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        String t = RegexSafety.truncateString(text, RegexSafety.MAX_QUERY_TEXT_FOR_REGEX);
        String cleaned = t.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").toLowerCase();
        List<String> stopwords = List.of(
                "cuantos", "cuántos", "quienes", "qué", "que", "dónde", "cuando", "como", "para", "con", "sobre",
                "fue", "esto", "esta", "hay", "había", "del", "los", "las", "una", "the", "a", "an", "and", "or", "but",
                "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "being"
        );
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !stopwords.contains(word) && word.length() > 2)
                .distinct()
                .toList();
    }

    private static <T> boolean isSimilarToCluster(T item, Cluster<T> cluster,
                                                   Function<T, String> contentExtractor,
                                                   Function<T, String> typeExtractor,
                                                   double threshold) {
        if (!typeExtractor.apply(item).equals(typeExtractor.apply(cluster.getRepresentativeItem()))) {
            return false;
        }
        String itemContent = contentExtractor.apply(item).toLowerCase();
        String clusterContent = contentExtractor.apply(cluster.getRepresentativeItem()).toLowerCase();
        Set<String> itemWords = new HashSet<>(Arrays.asList(itemContent.split("\\s+")));
        Set<String> clusterWords = new HashSet<>(Arrays.asList(clusterContent.split("\\s+")));
        long commonWords = itemWords.stream().filter(clusterWords::contains).count();
        double similarity = (double) commonWords / Math.max(itemWords.size(), clusterWords.size());
        return similarity > threshold;
    }
}
