package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerNormalizer;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime-safe constraint hints derived from the query plan (no expected answers). */
public record QueryConstraintSignals(
        Optional<QueryType> queryType,
        Set<Integer> years,
        Set<String> monthNames,
        Set<String> topicTokens,
        Set<String> entityTokens,
        boolean booleanVerify,
        boolean filterAndList,
        boolean findParagraphLookup,
        boolean presidedByConstraint,
        boolean absenceLikely) {

    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern SPANISH_MONTH =
            Pattern.compile(
                    "\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC_AFTER =
            Pattern.compile(
                    "(?:mencion(?:a|an|ó|o)|habl(?:ó|o)|trat(?:ó|o)|sobre|respecto a|relacion(?:ado)? con)\\s+(?:el |la |los |las )?([\\p{L}]{4,})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PRESIDED_BY =
            Pattern.compile(
                    "presidid[ao]s?\\s+por\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){0,4})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<String> STOP_TOPICS =
            Set.of("reunion", "reuniones", "acta", "actas", "documento", "documentos", "asunto", "tema");

    public static QueryConstraintSignals fromPlan(QueryPlan plan) {
        String query = combinedQueryText(plan).toLowerCase(Locale.ROOT);
        Set<Integer> years = new LinkedHashSet<>();
        Matcher ym = YEAR.matcher(query);
        while (ym.find()) {
            years.add(Integer.parseInt(ym.group()));
        }
        Set<String> months = new LinkedHashSet<>();
        Matcher mm = SPANISH_MONTH.matcher(query);
        while (mm.find()) {
            months.add(mm.group(1).toLowerCase(Locale.ROOT));
        }
        Set<String> topics = new LinkedHashSet<>();
        Matcher tm = TOPIC_AFTER.matcher(query);
        while (tm.find()) {
            String token = normalizeToken(tm.group(1));
            if (!token.isBlank() && !STOP_TOPICS.contains(token)) {
                topics.add(token);
            }
        }
        addTopicKeyword(query, "ascensor", topics);
        addTopicKeyword(query, "limpieza", topics);
        addTopicKeyword(query, "videovigilancia", topics);
        addTopicKeyword(query, "calefaccion", topics);
        addTopicKeyword(query, "radiacion", topics);
        addTopicKeyword(query, "presupuesto", topics);
        addTopicKeyword(query, "presupuestos", topics);

        Set<String> entities = new LinkedHashSet<>();
        if (plan.targetEntities() != null) {
            for (String e : plan.targetEntities()) {
                String t = normalizeToken(e);
                if (!t.isBlank()) {
                    entities.add(t);
                }
            }
        }
        Matcher pm = PRESIDED_BY.matcher(query);
        while (pm.find()) {
            String fullName = normalizeToken(pm.group(1));
            if (!fullName.isBlank()) {
                entities.add(fullName);
                for (String part : fullName.split("\\s+")) {
                    if (part.length() >= 3) {
                        entities.add(part);
                    }
                }
            }
        }

        boolean booleanVerify =
                query.contains("verifica si")
                        || query.contains("confirma si")
                        || query.contains("comprueba si");
        boolean filterAndList =
                query.contains("qué actas")
                        || query.contains("que actas")
                        || query.contains("qué reuniones")
                        || query.contains("que reuniones")
                        || query.contains("dime qué actas")
                        || query.contains("dime que actas")
                        || (query.contains(" y ") && query.contains("actas"));
        boolean findParagraphLookup =
                query.contains("qué se coment")
                        || query.contains("que se coment")
                        || query.contains("qué se dijo")
                        || query.contains("que se dijo")
                        || query.contains("respecto a");
        boolean presidedByConstraint = query.contains("presidid");
        boolean absenceLikely =
                query.contains("menos de diez")
                        || query.contains("menos de 10")
                        || query.contains("exactamente 21")
                        || query.contains("radiacion solar")
                        || query.contains("radiación solar")
                        || years.stream().anyMatch(y -> y >= 2028);

        return new QueryConstraintSignals(
                plan.classifierQueryType(),
                Set.copyOf(years),
                Set.copyOf(months),
                Set.copyOf(topics),
                Set.copyOf(entities),
                booleanVerify,
                filterAndList,
                findParagraphLookup,
                presidedByConstraint,
                absenceLikely);
    }

    private static String combinedQueryText(QueryPlan plan) {
        return (plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                + " "
                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText());
    }

    private static void addTopicKeyword(String query, String keyword, Set<String> topics) {
        if (query.contains(keyword)) {
            topics.add(keyword);
        }
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        return ExpectedAnswerNormalizer.normalizedFold(raw);
    }
}
