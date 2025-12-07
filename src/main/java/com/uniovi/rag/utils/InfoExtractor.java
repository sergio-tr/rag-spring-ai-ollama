package com.uniovi.rag.utils;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InfoExtractor {

    public static boolean containsAnyKeyword(String text, String[] keywords) {
        String lower = text.toLowerCase();
        return Arrays.stream(keywords)
                .map(String::toLowerCase)
                .anyMatch(lower::contains);
    }

    public static boolean containsRelevantPhrase(String text, String query) {
        String[] words = query.toLowerCase().split("\\s+");
        long hits = Arrays.stream(words).filter(text::contains).count();
        return hits >= 1;
    }


    public static String extractRelevantFragment(String content, String query) {
        if (query == null || query.isBlank()) return "";

        String cleanedQuery = query.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").toLowerCase();
        String[] keywords = extractSignificantKeywords(cleanedQuery).toArray(new String[0]);

        int bestMatchIdx = -1;
        int maxHits = 0;

        String contentLower = content.toLowerCase();

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

        if (bestMatchIdx == -1) return content.length() > 300 ? content.substring(0, 300) + "..." : content;

        int start = Math.max(0, bestMatchIdx - 80);
        int end = Math.min(content.length(), bestMatchIdx + 300);
        String snippet = content.substring(start, end).strip();

        // Intenta cerrar la frase si se corta
        if (!snippet.endsWith(".") && end < content.length()) {
            int nextDot = content.indexOf('.', end);
            if (nextDot != -1) {
                snippet += content.substring(end, Math.min(nextDot + 1, content.length()));
            }
        }

        return snippet + (end < content.length() ? "..." : "");
    }

    public String extractName(String text) {
        Matcher m = Pattern.compile(
                        "(?i)(presidente|secretario|asistido por|presidida por|secretariado por)\\s+(\\p{L}+\\s\\p{L}+(\\s\\p{L}+)*)"
                )
                .matcher(text);
        return m.find() ? m.group(2).trim() : null;
    }

    public static String extractDate(String content) {
        Matcher matcher = Pattern.compile("(?i)Fecha:\\s*(\\d{1,2} de [a-záéíóú]+ de \\d{4})").matcher(content);
        return matcher.find() ? matcher.group(1) : "Unknown date";
    }

    public static String extractTime(String content, String type) {
        Pattern pattern = type.equals("start")
                ? Pattern.compile("(?i)hora de inicio:\\s*(\\d{1,2}:\\d{2})")
                : Pattern.compile("(?i)(hora de finalización|hora de fin):\\s*(\\d{1,2}:\\d{2})");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(matcher.groupCount()) : null;
    }

    public static List<String> extractAttendees(String content) {
        List<String> attendees = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?m)^•\\s*(.+?)(?:\\s*\\((Presidente|Secretari[ao])\\))?$").matcher(content);
        while (matcher.find()) {
            attendees.add(matcher.group(1).trim());
        }
        return attendees;
    }

    public static String extractAgenda(String content) {
        Matcher matcher = Pattern.compile("(?i)Orden del d[íi]a:\\s*(.*?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)", Pattern.DOTALL).matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim().replaceAll("•", "-").replaceAll("\\n+", "\n- ");
        }
        return null;
    }

    public static String extractLiteralField(String field, String content) {
        return switch (field) {
            case "place" -> match(content, "(?i)Lugar:\\s*(.+)");
            case "date" -> extractDate(content);
            case "startTime" -> extractTime(content, "start");
            case "endTime" -> extractTime(content, "end");
            case "president" -> match(content, "(?i)•\\s*(.+?)\\s*\\(Presidente\\)");
            case "secretary" -> match(content, "(?i)•\\s*(.+?)\\s*\\(Secretari[ao]\\)");
            default -> null;
        };
    }


    public static int extractAttendeeCount(String content) {
        Matcher matcher = Pattern.compile("(?i)(\\d{1,2})\\s+propietarios").matcher(content);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public static int calculateDuration(String content) {
        Matcher startMatcher = Pattern.compile("(?i)Hora de inicio:\\s*(\\d{1,2}):(\\d{2})").matcher(content);
        Matcher endMatcher = Pattern.compile("(?i)Hora de finalización.*?(\\d{1,2}):(\\d{2})").matcher(content);
        if (startMatcher.find() && endMatcher.find()) {
            int start = Integer.parseInt(startMatcher.group(1)) * 60 + Integer.parseInt(startMatcher.group(2));
            int end = Integer.parseInt(endMatcher.group(1)) * 60 + Integer.parseInt(endMatcher.group(2));
            return end - start;
        }
        return 0;
    }

    public static int countProposals(String content) {
        Matcher matcher = Pattern.compile("(?i)(propuesta|se plantea|se acuerda|se discute)").matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    public static int countAgendaItems(String content) {
        Matcher matcher = Pattern.compile("(?i)Orden del d[íi]a:\\s*(.*?)(?=\\n\\n|Ruegos y preguntas|No habiendo más)", Pattern.DOTALL).matcher(content);
        if (matcher.find()) {
            String agenda = matcher.group(1);
            return (int) agenda.lines().filter(line -> line.strip().startsWith("•") || line.strip().startsWith("-")).count();
        }
        return 0;
    }

    public static int countQuestions(String content) {
        Matcher matcher = Pattern.compile("(?i)Ruegos y preguntas").matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }


    public static String compare(Map<String, MinuteInfo> summary, String type, String label) {
        return summary.entrySet().stream()
                .sorted((a, b) -> {
                    int va = getValue(a.getValue(), type);
                    int vb = getValue(b.getValue(), type);
                    return Integer.compare(vb, va); // descending
                })
                .map(e -> "- " + e.getKey() + ": " + getValue(e.getValue(), type) + " (" + label + ")")
                .reduce("Comparison based on " + label + ":\n", (a, b) -> a + b + "\n");
    }

    public static int getValue(MinuteInfo info, String type) {
        return switch (type) {
            case "attendees" -> info.attendees();
            case "duration" -> info.duration();
            case "proposals" -> info.proposals();
            default -> 0;
        };
    }

    public record MinuteInfo(
            String date,
            int attendees,
            int duration,
            int proposals,
            int agendaItems,
            int questions,
            String location
    ) {
        @Override
        public String toString() {
            return date + ": " + attendees + " asistentes, " + duration + " minutos, "
                    + proposals + " propuestas, " + agendaItems + " puntos del orden del día, "
                    + questions + " ruegos/preguntas, lugar: " + location;
        }
    }


    private static String match(String content, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    // ============================================================================
    // GENERIC UTILITY METHODS FOR CLUSTERING AND ANALYSIS
    // ============================================================================

    /**
     * Generic clustering method for any type of items
     */
    public static <T> List<Cluster<T>> clusterItems(List<T> items, 
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

    /**
     * Checks if an item is similar to a cluster
     */
    public static <T> boolean isSimilarToCluster(T item, Cluster<T> cluster, 
                                                Function<T, String> contentExtractor,
                                                Function<T, String> typeExtractor,
                                                double threshold) {
        // Check if types match
        if (!typeExtractor.apply(item).equals(typeExtractor.apply(cluster.getRepresentativeItem()))) {
            return false;
        }
        
        // Check content similarity
        String itemContent = contentExtractor.apply(item).toLowerCase();
        String clusterContent = contentExtractor.apply(cluster.getRepresentativeItem()).toLowerCase();
        
        Set<String> itemWords = Set.of(itemContent.split("\\s+"));
        Set<String> clusterWords = Set.of(clusterContent.split("\\s+"));
        
        long commonWords = itemWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(itemWords.size(), clusterWords.size());
        
        return similarity > threshold;
    }

    /**
     * Validates if a relevance score is valid
     */
    public static boolean isValidRelevanceScore(String scoreText) {
        try {
            double score = Double.parseDouble(scoreText.strip());
            return score >= 0.0 && score <= 1.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Formats cluster summary for display
     */
    public static <T> String formatClusterSummary(List<Cluster<T>> clusters, 
                                                Function<T, String> itemFormatter,
                                                Function<T, String> typeExtractor) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            Cluster<T> cluster = clusters.get(i);
            summary.append(String.format("Cluster %d (%d items) - Type: %s\n", 
                                        i + 1, cluster.getSize(), typeExtractor.apply(cluster.getRepresentativeItem())));
            summary.append(itemFormatter.apply(cluster.getRepresentativeItem()));
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for display
     */
    public static <T> String formatClusterAnalysis(List<Cluster<T>> clusters, 
                                                  Function<T, Double> scoreExtractor,
                                                  Function<T, String> typeExtractor) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            Cluster<T> cluster = clusters.get(i);
            double avgScore = cluster.getItems().stream()
                    .mapToDouble(scoreExtractor::apply)
                    .average()
                    .orElse(0.0);
            
            analysis.append(String.format("- Cluster %d: %d items, avg relevance: %.2f, type: %s\n", 
                                        i + 1, cluster.getSize(), avgScore, 
                                        typeExtractor.apply(cluster.getRepresentativeItem())));
        }
        
        return analysis.toString();
    }

    /**
     * Calculates similarity between two text strings
     */
    public static double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        
        String lower1 = text1.toLowerCase();
        String lower2 = text2.toLowerCase();
        
        Set<String> words1 = Set.of(lower1.split("\\s+"));
        Set<String> words2 = Set.of(lower2.split("\\s+"));
        
        long commonWords = words1.stream()
                .filter(words2::contains)
                .count();
        
        return (double) commonWords / Math.max(words1.size(), words2.size());
    }

    /**
     * Extracts significant keywords from text
     */
    public static List<String> extractSignificantKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        
        String cleaned = text.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").toLowerCase();
        List<String> stopwords = List.of(
                "cuantos", "cuántos", "quienes", "qué", "que", "dónde", "cuando", "como", "para", "con", "sobre",
                "fue", "esto", "esta", "hay", "había", "del", "los", "las", "una", "the", "a", "an", "and", "or", "but",
                "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was", "were", "be", "been", "being"
        );

        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !stopwords.contains(word) && word.length() > 2)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Finds the best matching text fragment based on keywords
     */
    public static String findBestMatchingFragment(String content, String query) {
        if (content == null || query == null || content.isBlank() || query.isBlank()) {
            return content != null ? content : "";
        }

        List<String> keywords = extractSignificantKeywords(query);
        if (keywords.isEmpty()) {
            return content.length() > 300 ? content.substring(0, 300) + "..." : content;
        }

        String contentLower = content.toLowerCase();
        int bestMatchIdx = -1;
        int maxHits = 0;

        for (int i = 0; i < contentLower.length(); i++) {
            int hits = 0;
            for (String keyword : keywords) {
                if (i + keyword.length() <= contentLower.length() && 
                    contentLower.substring(i).startsWith(keyword)) {
                    hits++;
                }
            }
            if (hits > maxHits) {
                maxHits = hits;
                bestMatchIdx = i;
            }
        }

        if (bestMatchIdx == -1) {
            return content.length() > 300 ? content.substring(0, 300) + "..." : content;
        }

        int start = Math.max(0, bestMatchIdx - 80);
        int end = Math.min(content.length(), bestMatchIdx + 300);
        String snippet = content.substring(start, end).strip();

        // Try to close the sentence if it's cut off
        if (!snippet.endsWith(".") && end < content.length()) {
            int nextDot = content.indexOf('.', end);
            if (nextDot != -1) {
                snippet += content.substring(end, Math.min(nextDot + 1, content.length()));
            }
        }

        return snippet + (end < content.length() ? "..." : "");
    }

    /**
     * Generic cluster class for grouping similar items
     */
    public static class Cluster<T> {
        private final List<T> items = new ArrayList<>();

        public Cluster(T initialItem) {
            items.add(initialItem);
        }

        public void addItem(T item) {
            items.add(item);
        }

        public int getSize() {
            return items.size();
        }

        public List<T> getItems() {
            return new ArrayList<>(items);
        }

        public T getRepresentativeItem() {
            return items.get(0); // Simple implementation - could be enhanced
        }

        @Override
        public String toString() {
            return String.format("Cluster[%d items, representative: %s]", 
                               items.size(), getRepresentativeItem().toString());
        }
    }

}