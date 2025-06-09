package com.uniovi.rag.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static List<String> extractSignificantKeywords(String query) {
        String cleaned = query.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").toLowerCase();
        List<String> stopwords = List.of(
                "cuantos", "cuántos", "quienes", "qué", "que", "dónde", "cuando", "como", "para", "con", "sobre",
                "fue", "esto", "esta", "hay", "había", "del", "los", "las", "una"
        );

        return Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !stopwords.contains(word) && word.length() > 2)
                .distinct()
                .toList();
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

}