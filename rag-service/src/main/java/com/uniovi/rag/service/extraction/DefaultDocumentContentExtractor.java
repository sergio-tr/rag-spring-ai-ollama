package com.uniovi.rag.service.extraction;

import com.uniovi.rag.model.Cluster;
import com.uniovi.rag.util.RegexSafety;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default implementation of document/minute content extraction.
 * Consolidates logic previously in utils for clearer ownership and testability.
 */
public class DefaultDocumentContentExtractor implements DocumentContentExtractor {

    @Override
    public String extractDate(String content) {
        if (content == null) {
            return "Unknown date";
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(?i)Fecha:\\s*(\\d{1,2} de [a-záéíóú]+ de \\d{4})").matcher(c);
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
        int bestMatchIdx = -1;
        int maxHits = 0;
        String contentLower = boundedContent.toLowerCase();
        for (int i = 0; i < contentLower.length(); i++) {
            int hits = 0;
            for (String word : keywords) {
                if (i + word.length() <= contentLower.length() && contentLower.substring(i).startsWith(word)) {
                    hits++;
                }
            }
            if (hits > maxHits) {
                maxHits = hits;
                bestMatchIdx = i;
            }
        }
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
                ? Pattern.compile("(?i)hora de inicio:\\s*(\\d{1,2}:\\d{2})")
                : Pattern.compile("(?i)(hora de finalización|hora de fin):\\s*(\\d{1,2}:\\d{2})");
        Matcher matcher = pattern.matcher(c);
        return matcher.find() ? matcher.group(matcher.groupCount()) : null;
    }

    @Override
    public int extractAttendeeCount(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(?i)(\\d{1,2})\\s+propietarios").matcher(c);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    @Override
    public int calculateDuration(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher startMatcher = Pattern.compile("(?i)Hora de inicio:\\s*(\\d{1,2}):(\\d{2})").matcher(c);
        Matcher endMatcher = Pattern.compile("(?i)Hora de finalización.*?(\\d{1,2}):(\\d{2})").matcher(c);
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
                return match(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
            case "secretary":
                return match(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
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
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(?m)^•\\s*(.+?)(?:\\s*\\((Presidente|Secretari[ao])\\))?$").matcher(c);
        while (matcher.find()) {
            attendees.add(matcher.group(1).trim());
        }
        return attendees;
    }

    @Override
    public String extractAgenda(String content) {
        if (content == null) {
            return null;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(?i)Orden del d[íi]a:\\s*(.+?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)", Pattern.DOTALL).matcher(c);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("•", "-").replaceAll("\\n+", "\n- ");
        }
        return null;
    }

    @Override
    public int countProposals(String content) {
        if (content == null) {
            return 0;
        }
        String c = RegexSafety.truncateString(content, RegexSafety.MAX_DOCUMENT_TEXT_FOR_REGEX);
        Matcher matcher = Pattern.compile("(?i)(propuesta|se plantea|se acuerda|se discute)").matcher(c);
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
        Matcher matcher = Pattern.compile("(?i)Orden del d[íi]a:\\s*(.+?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)", Pattern.DOTALL).matcher(c);
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
        Matcher matcher = Pattern.compile("(?i)Ruegos y preguntas").matcher(c);
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
