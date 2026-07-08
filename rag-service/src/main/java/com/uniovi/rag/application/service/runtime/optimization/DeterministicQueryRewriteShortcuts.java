package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic query rewrite for common Spanish acta-corpus patterns (no LLM). */
public final class DeterministicQueryRewriteShortcuts {

    public enum IntentKind {
        FIND_ACTA_BY_TOPIC,
        LIST_ACTAS_BY_ATTRIBUTE,
        LIST_LOCATIONS,
        LIST_ATTENDEES,
        PERSON_ATTENDANCE,
        TOPIC_SUMMARY
    }

    private static final Pattern FIND_ACTAS_TOPIC =
            Pattern.compile(
                    "(?isu)(?:en qué actas|en que actas)\\s+(?:se\\s+)?(?:habla|hablan|mencionan|tratan)\\s+(?:sobre|de|acerca de)\\s+(.+?)\\??\\s*$");
    private static final Pattern FIND_ACTA_TOPIC =
            Pattern.compile(
                    "(?isu)(?:en qué acta|en que acta)\\s+(?:se\\s+)?(?:habl[oó]|mencion[oó]|trat[oó]|coment[oó])\\s+(?:sobre|de|acerca de)\\s+(.+?)\\??\\s*$");
    private static final Pattern LIST_ACTAS_ATTRIBUTE =
            Pattern.compile(
                    "(?isu)(?:dime|indica|lista)\\s+(?:qué|que)\\s+actas\\s+(?:tienen|con)\\s+(\\d+)\\s+asistentes?\\s*\\??\\s*$");
    private static final Pattern LIST_LOCATIONS =
            Pattern.compile("(?isu)(?:dime|indica)\\s+(?:los\\s+)?lugares\\s+donde\\s+se\\s+han\\s+realizado\\s+las\\s+actas\\s*\\??\\s*$");
    private static final Pattern PERSON_ATTENDANCE =
            Pattern.compile("(?isu)(?:a qué actas|a que actas)\\s+asiste\\s+(.+?)\\s*\\??\\s*$");
    private static final Pattern TOPIC_SUMMARY =
            Pattern.compile(
                    "(?isu)(?:dime|indica)\\s+(?:qué|que)\\s+se\\s+ha\\s+hablado\\s+sobre\\s+(.+?)\\s*\\??\\s*$");

    private static final Pattern TOPIC_COUNT =
            Pattern.compile(
                    "(?isu)(?:en\\s+cuántas|en\\s+cuantas|dime\\s+en\\s+cuántas|dime\\s+en\\s+cuantas)\\s+reuniones\\s+se\\s+trat(?:ó|o)\\s+(?:el\\s+asunto\\s+de\\s+)?(.+?)\\s*\\??\\s*$");

    private static final Map<String, List<String>> TOPIC_ALIASES =
            Map.of(
                    "terrazas", List.of("zonas comunes", "terraza"),
                    "zonas comunes", List.of("terrazas", "terraza"),
                    "videovigilancia", List.of("cámaras", "camaras", "cámaras de seguridad", "vigilancia"),
                    "convivencia", List.of("normas de convivencia", "reglamento", "normas convivencia"),
                    "calefacción", List.of("calefaccion", "climatización", "climatizacion"),
                    "calefaccion", List.of("calefacción", "climatización", "climatizacion"));

    private DeterministicQueryRewriteShortcuts() {}

    public static Optional<StructuredRewriteResult> tryRewrite(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Optional.empty();
        }
        String text = normalizedText.trim();

        Matcher findActas = FIND_ACTAS_TOPIC.matcher(text);
        if (findActas.matches()) {
            String topic = expandTopicSynonyms(findActas.group(1).trim());
            return Optional.of(build(text, topic, IntentKind.FIND_ACTA_BY_TOPIC, List.of(topic)));
        }

        Matcher findActa = FIND_ACTA_TOPIC.matcher(text);
        if (findActa.matches()) {
            String topic = expandTopicSynonyms(findActa.group(1).trim());
            return Optional.of(build(text, topic, IntentKind.FIND_ACTA_BY_TOPIC, List.of(topic)));
        }

        Matcher listAttr = LIST_ACTAS_ATTRIBUTE.matcher(text);
        if (listAttr.matches()) {
            String count = listAttr.group(1).trim();
            String rewritten = "actas con " + count + " asistentes";
            return Optional.of(
                    build(
                            text,
                            rewritten,
                            IntentKind.LIST_ACTAS_BY_ATTRIBUTE,
                            List.of(count, "asistentes"),
                            Map.of("attendeeCount", count)));
        }

        if (LIST_LOCATIONS.matcher(text).matches()) {
            return Optional.of(
                    build(
                            text,
                            "lugares sala reunión celebrada en actas",
                            IntentKind.LIST_LOCATIONS,
                            List.of("lugares", "sala", "reunión")));
        }

        Matcher person = PERSON_ATTENDANCE.matcher(text);
        if (person.matches()) {
            String name = person.group(1).trim();
            return Optional.of(
                    build(
                            text,
                            "asistencia participantes " + name,
                            IntentKind.PERSON_ATTENDANCE,
                            List.of(name)));
        }

        Matcher topic = TOPIC_SUMMARY.matcher(text);
        if (topic.matches()) {
            String subject = topic.group(1).trim();
            return Optional.of(
                    build(text, subject + " mejoras actas", IntentKind.TOPIC_SUMMARY, List.of(subject)));
        }

        Matcher topicCount = TOPIC_COUNT.matcher(text);
        if (topicCount.matches()) {
            String subject = expandTopicSynonyms(topicCount.group(1).trim());
            return Optional.of(
                    build(
                            text,
                            subject + " reuniones actas",
                            IntentKind.FIND_ACTA_BY_TOPIC,
                            List.of(subject)));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        Optional<StructuredRewriteResult> aliasExpanded = tryTopicAliasRewrite(text, lower);
        if (aliasExpanded.isPresent()) {
            return aliasExpanded;
        }
        if (lower.contains("zonas comunes") && lower.contains("en qué acta")) {
            return Optional.of(
                    build(
                            text,
                            "zonas comunes actas",
                            IntentKind.FIND_ACTA_BY_TOPIC,
                            List.of("zonas comunes")));
        }

        return Optional.empty();
    }

    private static Optional<StructuredRewriteResult> tryTopicAliasRewrite(String text, String lower) {
        for (Map.Entry<String, List<String>> entry : TOPIC_ALIASES.entrySet()) {
            if (!lower.contains(entry.getKey())) {
                continue;
            }
            StringBuilder rewritten = new StringBuilder(text.toLowerCase(Locale.ROOT));
            for (String alias : entry.getValue()) {
                if (!rewritten.toString().contains(alias)) {
                    rewritten.append(' ').append(alias);
                }
            }
            return Optional.of(
                    build(
                            text,
                            rewritten.toString().trim(),
                            IntentKind.FIND_ACTA_BY_TOPIC,
                            List.of(entry.getKey())));
        }
        return Optional.empty();
    }

    private static String expandTopicSynonyms(String topic) {
        String lower = topic.toLowerCase(Locale.ROOT);
        StringBuilder expanded = new StringBuilder(topic.trim());
        for (Map.Entry<String, List<String>> entry : TOPIC_ALIASES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                for (String alias : entry.getValue()) {
                    if (!lower.contains(alias)) {
                        expanded.append(' ').append(alias);
                    }
                }
            }
        }
        return expanded.toString().trim();
    }

    public static Optional<IntentKind> matches(String normalizedText) {
        return tryRewrite(normalizedText).map(r -> IntentKind.valueOf(r.rewriteNotes().get(0).replace("intent=", "")));
    }

    private static StructuredRewriteResult build(
            String original, String rewritten, IntentKind intent, List<String> entities) {
        return build(original, rewritten, intent, entities, Map.of());
    }

    private static StructuredRewriteResult build(
            String original,
            String rewritten,
            IntentKind intent,
            List<String> entities,
            Map<String, String> slots) {
        boolean applied = !rewritten.equalsIgnoreCase(original);
        List<String> notes = new ArrayList<>();
        notes.add("intent=" + intent.name());
        notes.add("DETERMINISTIC_SHORTCUT");
        return new StructuredRewriteResult(
                rewritten,
                applied,
                notes,
                StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                List.copyOf(entities),
                List.of(),
                Optional.empty(),
                Map.copyOf(new LinkedHashMap<>(slots)),
                List.of("preserve_topic_terms"));
    }
}
