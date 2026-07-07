package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.application.service.runtime.DateGroundingSupport;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.factual.FactualConstraintType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FactualConstraintExtractor {

    private static final Pattern QUOTED = Pattern.compile("[\"«]([^\"»]{2,80})[\"»]");
    private static final Pattern RESPECTO_A = Pattern.compile("respecto a (?:la |el )?([^?.!,;]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SOBRE = Pattern.compile("sobre (?:la |el )?([^?.!,;]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MENCIONA = Pattern.compile("mencion(?:an|a|o) (?:la |el )?([^?.!,;]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HABLO_DE = Pattern.compile("(?:se )?hablo de (?:la |el |los |las )?([^?.!,;]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXACTLY = Pattern.compile("exactamente\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AT_LEAST = Pattern.compile("(?:más de|mas de|al menos)\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AT_MOST = Pattern.compile("(?:menos de|como máximo|como maximo)\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern COUNT_SHAPE =
            Pattern.compile("\\b(cuántas|cuantas|cuántos|cuantos|número de|numero de|en cuántas|en cuantas|cuánto dur|cuanto dur|duración|duracion)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ABSENCE_SHAPE =
            Pattern.compile(
                    "\\b(se hablo|se comento|ninguna|no existen|no hay|no se menciona|no se encuentra)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC_ABSENCE_INQUIRY =
            Pattern.compile(
                    "(?:que se comento|qué se comentó|que se dijo|qué se dijo|que se menciono|qué se mencionó)"
                            + "\\s+(?:respecto|sobre)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC_INQUIRY_RESPECTO =
            Pattern.compile(
                    "\\b(?:qué|que)\\s+(?:se comentó|se comento|se dijo|se mencionó|se menciono)\\s+respecto\\s+a\\s+",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC_INQUIRY_SOBRE =
            Pattern.compile(
                    "\\b(?:qué|que)\\s+(?:se comentó|se comento|se dijo|se mencionó|se menciono)\\s+sobre\\s+",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC_INQUIRY_HABLO =
            Pattern.compile(
                    "\\b(?:qué|que|en qué|en que)\\s+.*\\b(?:se habló|se hablo)\\s+de\\s+",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern THRESHOLD_ATTENDANCE_ABSENCE =
            Pattern.compile("participaron\\s+menos\\s+de", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BOOLEAN_VERIFY =
            Pattern.compile("\\b(verifica|confirma)\\s+si\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FUTURE_YEAR = Pattern.compile("\\b(202[89]|20[3-9]\\d)\\b");
    private static final int UNAVAILABLE_YEAR_THRESHOLD = 2028;

    public static String constraintsPromptBlock(FactualQuestionConstraints constraints) {
        if (constraints == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<QuestionConstraints>\n");
        constraints.requiredDateIso().ifPresent(d -> sb.append("- Required date: ").append(d).append('\n'));
        if (!constraints.topicPhrases().isEmpty()) {
            sb.append("- Required topic phrases: ").append(String.join("; ", constraints.topicPhrases())).append('\n');
        }
        constraints.requiredEntity().ifPresent(e -> sb.append("- Required entity: ").append(e).append('\n'));
        constraints.numericConstraint().ifPresent(n -> sb.append("- Numeric constraint: ").append(n.kind()).append(' ').append(n.value()).append('\n'));
        if (constraints.absenceQuestion()) {
            sb.append("- Answer shape: absence or negative evidence\n");
        }
        sb.append("</QuestionConstraints>");
        return sb.toString();
    }

    private FactualConstraintExtractor() {}

    public static FactualQuestionConstraints extract(String question, QueryPlan plan, Optional<QueryType> labQueryTypeExpected) {
        String q = question != null ? question.trim() : "";
        boolean documentBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        EntityExtractionResult ner = plan != null ? plan.entityExtractionResult() : EntityExtractionResult.emptyWithNote("");
        Optional<String> dateIso =
                DateGroundingSupport.requestedDate(q, ner.dates()).map(d -> d.value());
        List<String> topics = extractTopicPhrases(q, ner);
        Optional<String> entity = extractEntity(q, ner);
        Optional<FactualQuestionConstraints.NumericConstraint> numeric = extractNumeric(q);
        boolean absence = isAbsenceQuestion(q, dateIso, numeric);
        QueryType hinted = labQueryTypeExpected.orElse(null);
        AnswerGroundingPolicy policy =
                FactualGroundingPolicySelector.selectPolicy(q, documentBound, hinted, dateIso, topics, entity, numeric, absence);
        FactualConstraintType type = constraintType(dateIso, topics, entity, numeric);
        return new FactualQuestionConstraints(policy, type, dateIso, topics, entity, numeric, absence, documentBound);
    }

    static FactualConstraintType constraintType(
            Optional<String> dateIso,
            List<String> topics,
            Optional<String> entity,
            Optional<FactualQuestionConstraints.NumericConstraint> numeric) {
        boolean hasDate = dateIso.isPresent();
        boolean hasTopic = !topics.isEmpty();
        boolean hasEntity = entity.isPresent();
        boolean hasNumeric = numeric.isPresent();
        int kinds = (hasDate ? 1 : 0) + (hasTopic ? 1 : 0) + (hasEntity ? 1 : 0) + (hasNumeric ? 1 : 0);
        if (kinds >= 2) {
            return FactualConstraintType.MIXED;
        }
        if (hasDate) {
            return FactualConstraintType.DATE;
        }
        if (hasNumeric) {
            return FactualConstraintType.NUMERIC;
        }
        if (hasEntity) {
            return FactualConstraintType.ENTITY;
        }
        if (hasTopic) {
            return FactualConstraintType.TOPIC;
        }
        return FactualConstraintType.NONE;
    }

    static boolean isAbsenceQuestion(
            String q,
            Optional<String> dateIso,
            Optional<FactualQuestionConstraints.NumericConstraint> numeric) {
        String n = normalize(q);
        if (ABSENCE_SHAPE.matcher(n).find()) {
            return true;
        }
        if (THRESHOLD_ATTENDANCE_ABSENCE.matcher(n).find()) {
            return true;
        }
        if (TOPIC_ABSENCE_INQUIRY.matcher(q).find()) {
            return true;
        }
        if (TOPIC_INQUIRY_RESPECTO.matcher(q).find()
                || TOPIC_INQUIRY_SOBRE.matcher(q).find()
                || TOPIC_INQUIRY_HABLO.matcher(q).find()) {
            return true;
        }
        if (BOOLEAN_VERIFY.matcher(n).find() && n.contains(" no ")) {
            return true;
        }
        if (dateIso.isPresent() && isUnavailableYear(dateIso.get())) {
            return true;
        }
        Matcher future = FUTURE_YEAR.matcher(q != null ? q : "");
        while (future.find()) {
            try {
                if (Integer.parseInt(future.group(1)) >= UNAVAILABLE_YEAR_THRESHOLD) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (numeric.isPresent() && EXACTLY.matcher(n).find()) {
            return true;
        }
        return false;
    }

    static List<String> extractTopicPhrases(String q, EntityExtractionResult ner) {
        Set<String> out = new LinkedHashSet<>();
        if (q != null) {
            Matcher quoted = QUOTED.matcher(q);
            while (quoted.find()) {
                addPhrase(out, quoted.group(1));
            }
            addRegexPhrase(out, RESPECTO_A, q);
            addRegexPhrase(out, SOBRE, q);
            addRegexPhrase(out, MENCIONA, q);
            addRegexPhrase(out, HABLO_DE, normalize(q));
        }
        if (ner != null) {
            for (String topic : ner.topics()) {
                addPhrase(out, topic);
            }
        }
        return List.copyOf(out);
    }

    static Optional<String> extractEntity(String q, EntityExtractionResult ner) {
        if (ner != null) {
            for (String person : ner.people()) {
                if (person != null && person.trim().length() >= 3) {
                    return Optional.of(person.trim());
                }
            }
        }
        if (q == null) {
            return Optional.empty();
        }
        Matcher confirm = Pattern.compile("confirma si (.+?) aparece", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(q);
        if (confirm.find()) {
            return Optional.of(confirm.group(1).trim());
        }
        return Optional.empty();
    }

    static Optional<FactualQuestionConstraints.NumericConstraint> extractNumeric(String q) {
        if (q == null || q.isBlank()) {
            return Optional.empty();
        }
        Matcher ex = EXACTLY.matcher(q);
        if (ex.find()) {
            return Optional.of(new FactualQuestionConstraints.NumericConstraint(
                    FactualQuestionConstraints.ComparatorKind.EXACTLY, Integer.parseInt(ex.group(1))));
        }
        Matcher atLeast = AT_LEAST.matcher(q);
        if (atLeast.find()) {
            return Optional.of(new FactualQuestionConstraints.NumericConstraint(
                    FactualQuestionConstraints.ComparatorKind.AT_LEAST, Integer.parseInt(atLeast.group(1))));
        }
        Matcher atMost = AT_MOST.matcher(q);
        if (atMost.find()) {
            return Optional.of(new FactualQuestionConstraints.NumericConstraint(
                    FactualQuestionConstraints.ComparatorKind.AT_MOST, Integer.parseInt(atMost.group(1))));
        }
        if (COUNT_SHAPE.matcher(q).find()) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static String normalize(String text) {
        return FactualTextSupport.normalize(text);
    }

    private static void addRegexPhrase(Set<String> out, Pattern pattern, String q) {
        Matcher m = pattern.matcher(q);
        if (m.find()) {
            addPhrase(out, m.group(1));
        }
    }

    static boolean isUnavailableYear(String dateIso) {
        if (dateIso == null || dateIso.isBlank()) {
            return false;
        }
        String yearToken = dateIso.length() >= 4 ? dateIso.substring(0, 4) : dateIso;
        if (!yearToken.matches("\\d{4}")) {
            return false;
        }
        try {
            return Integer.parseInt(yearToken) >= UNAVAILABLE_YEAR_THRESHOLD;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void addPhrase(Set<String> out, String raw) {
        if (raw == null) {
            return;
        }
        String t = raw.trim().replaceAll("[?.!,;]+$", "").trim();
        if (t.length() >= 3) {
            out.add(t);
        }
    }
}
