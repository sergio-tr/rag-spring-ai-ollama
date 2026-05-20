package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.GenerationSummaryMetrics;
import com.uniovi.rag.application.result.evaluation.JudgeSummarizableRow;
import com.uniovi.rag.application.result.evaluation.RetrievalSummaryMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggregates per-item judge rows into a typed {@link EvaluationSummary}.
 */
public final class EvaluationSummaryBuilder {

    private static final int BLEU_MAX_N = 4;
    private static final int DEFAULT_K = 5;
    private static final String KEY_MEAN_CONTEXT_SUFFICIENCY = "mean_context_sufficiency";

    private static final Pattern CORRECTNESS_LINE = Pattern.compile(
            "Correctness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXT_SUFFICIENCY_LINE = Pattern.compile(
            "Context\\s+Sufficiency\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEVANCE_LINE = Pattern.compile(
            "Relevance\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEPENDENCE_LINE = Pattern.compile(
            "Independence\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUNDEDNESS_LINE = Pattern.compile(
            "Groundedness\\s*:\\s*\\[?\\s*([1-5])\\.?\\d*\\s*\\]?",
            Pattern.CASE_INSENSITIVE);

    private EvaluationSummaryBuilder() {}

    public static EvaluationSummary summarize(List<? extends JudgeSummarizableRow> results) {
        if (results == null || results.isEmpty()) {
            return EvaluationSummary.ofMetrics(
                    new GenerationSummaryMetrics(null, null, null, null, null, null, 0, null, null, null),
                    new RetrievalSummaryMetrics(null, null, null, null));
        }

        List<Integer> correctness = new ArrayList<>();
        List<Integer> contextSufficiency = new ArrayList<>();
        List<Integer> relevance = new ArrayList<>();
        List<Integer> independence = new ArrayList<>();
        List<Integer> groundedness = new ArrayList<>();

        for (JudgeSummarizableRow r : results) {
            Map<String, Integer> scores = JudgeScoreParser.parseScores(r.llmEvaluation());
            addIfNotNull(correctness, scores.get("correctness"));
            addIfNotNull(contextSufficiency, scores.get("context_sufficiency"));
            addIfNotNull(relevance, scores.get("relevance"));
            addIfNotNull(independence, scores.get("independence"));
            addIfNotNull(groundedness, scores.get("groundedness"));
        }

        int total = correctness.size();
        long ge4 = correctness.stream().filter(s -> s != null && s >= 4).count();
        GenerationSummaryMetrics generation =
                new GenerationSummaryMetrics(
                        mean(correctness),
                        mean(contextSufficiency),
                        mean(relevance),
                        mean(independence),
                        mean(groundedness),
                        total > 0 ? (100.0 * ge4 / total) : null,
                        total,
                        computeBleu(results),
                        computeRougeL(results),
                        computeMeteor(results));

        RetrievalSummaryMetrics retrieval =
                new RetrievalSummaryMetrics(
                        generation.meanContextSufficiency(),
                        computePrecisionAtK(results, DEFAULT_K),
                        computeRecallAtK(results, DEFAULT_K),
                        computeMrr(results));

        return EvaluationSummary.ofMetrics(generation, retrieval);
    }

    private static void addIfNotNull(List<Integer> list, Integer value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static Double mean(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Integer v : values) {
            if (v != null) {
                sum += v;
            }
        }
        return sum / values.size();
    }

    private static Double computeBleu(List<? extends JudgeSummarizableRow> results) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            String ref = r.correctAnswer();
            String hyp = r.generatedAnswer();
            if (ref == null && hyp == null) {
                continue;
            }
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = bleuScore(refTok, hypTok);
            if (score >= 0) {
                sum += score;
                count++;
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static Double computeRougeL(List<? extends JudgeSummarizableRow> results) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            String ref = r.correctAnswer();
            String hyp = r.generatedAnswer();
            if (ref == null && hyp == null) {
                continue;
            }
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = rougeLScore(refTok, hypTok);
            if (score >= 0) {
                sum += score;
                count++;
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static Double computeMeteor(List<? extends JudgeSummarizableRow> results) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            String ref = r.correctAnswer();
            String hyp = r.generatedAnswer();
            if (ref == null && hyp == null) {
                continue;
            }
            List<String> refTok = tokenize(ref != null ? ref : "");
            List<String> hypTok = tokenize(hyp != null ? hyp : "");
            double score = meteorScore(refTok, hypTok);
            if (score >= 0) {
                sum += score;
                count++;
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static Double computePrecisionAtK(List<? extends JudgeSummarizableRow> results, int k) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            List<String> retrieved = r.retrievedDocumentIds();
            List<String> relevant = r.relevantDocumentIds();
            if (retrieved != null && !retrieved.isEmpty() && relevant != null && !relevant.isEmpty()) {
                Set<String> relSet = new HashSet<>(relevant);
                int atK = Math.min(k, retrieved.size());
                if (atK > 0) {
                    long hits = retrieved.subList(0, atK).stream().filter(relSet::contains).count();
                    sum += (double) hits / atK;
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static Double computeRecallAtK(List<? extends JudgeSummarizableRow> results, int k) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            List<String> retrieved = r.retrievedDocumentIds();
            List<String> relevant = r.relevantDocumentIds();
            if (retrieved != null && !retrieved.isEmpty() && relevant != null && !relevant.isEmpty()) {
                Set<String> relSet = new HashSet<>(relevant);
                int atK = Math.min(k, retrieved.size());
                if (atK > 0) {
                    long hits = retrieved.subList(0, atK).stream().filter(relSet::contains).count();
                    sum += (double) hits / relSet.size();
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static Double computeMrr(List<? extends JudgeSummarizableRow> results) {
        double sum = 0;
        int count = 0;
        for (JudgeSummarizableRow r : results) {
            List<String> retrieved = r.retrievedDocumentIds();
            List<String> relevant = r.relevantDocumentIds();
            if (retrieved == null || retrieved.isEmpty() || relevant == null || relevant.isEmpty()) {
                continue;
            }
            Set<String> relSet = new HashSet<>(relevant);
            for (int i = 0; i < retrieved.size(); i++) {
                if (relSet.contains(retrieved.get(i))) {
                    sum += 1.0 / (i + 1);
                    count++;
                    break;
                }
            }
        }
        return count > 0 ? sum / count : null;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase().trim().split("\\s+")) {
            String t = stripOuterNonTokenChars(w);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    static String stripOuterNonTokenChars(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = s.length();
        while (start < end && !isTokenChar(s.charAt(start))) {
            start++;
        }
        while (end > start && !isTokenChar(s.charAt(end - 1))) {
            end--;
        }
        return start == 0 && end == s.length() ? s : s.substring(start, end);
    }

    private static boolean isTokenChar(char c) {
        if (c >= '0' && c <= '9') {
            return true;
        }
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        return c == 'á' || c == 'é' || c == 'í' || c == 'ó' || c == 'ú' || c == 'ñ';
    }

    private static double bleuScore(List<String> ref, List<String> hyp) {
        if (hyp.isEmpty()) {
            return ref.isEmpty() ? 1.0 : 0.0;
        }
        double bp = ref.size() >= hyp.size() ? 1.0 : Math.exp(1.0 - (double) ref.size() / hyp.size());
        double logSum = 0;
        for (int n = 1; n <= BLEU_MAX_N; n++) {
            if (hyp.size() < n) {
                continue;
            }
            int matches = 0;
            Map<String, Integer> refCount = ngramCounts(ref, n);
            Map<String, Integer> hypCount = ngramCounts(hyp, n);
            for (Map.Entry<String, Integer> e : hypCount.entrySet()) {
                matches += Math.min(e.getValue(), refCount.getOrDefault(e.getKey(), 0));
            }
            int hypN = hypCount.values().stream().mapToInt(Integer::intValue).sum();
            if (hypN == 0) {
                continue;
            }
            double pn = (double) matches / hypN;
            if (pn > 0) {
                logSum += Math.log(pn) / BLEU_MAX_N;
            }
        }
        return bp * (logSum <= 0 ? 0 : Math.exp(logSum));
    }

    private static Map<String, Integer> ngramCounts(List<String> tokens, int n) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i <= tokens.size() - n; i++) {
            String ng = String.join(" ", tokens.subList(i, i + n));
            m.merge(ng, 1, Integer::sum);
        }
        return m;
    }

    private static double rougeLScore(List<String> ref, List<String> hyp) {
        if (ref.isEmpty() && hyp.isEmpty()) {
            return 1.0;
        }
        if (ref.isEmpty() || hyp.isEmpty()) {
            return 0.0;
        }
        int lcs = lcsLength(ref, hyp);
        double p = (double) lcs / hyp.size();
        double r = (double) lcs / ref.size();
        return (p + r > 0) ? 2.0 * p * r / (p + r) : 0.0;
    }

    private static int lcsLength(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.get(i - 1).equals(b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[n][m];
    }

    private static double meteorScore(List<String> ref, List<String> hyp) {
        if (ref.isEmpty() && hyp.isEmpty()) {
            return 1.0;
        }
        if (ref.isEmpty() || hyp.isEmpty()) {
            return 0.0;
        }
        Set<String> refSet = new HashSet<>(ref);
        Set<String> hypSet = new HashSet<>(hyp);
        int matches = 0;
        for (String w : hypSet) {
            if (refSet.contains(w)) {
                matches++;
            }
        }
        double p = (double) matches / hyp.size();
        double r = (double) matches / ref.size();
        double fMean = (p + r > 0) ? 10.0 * p * r / (9.0 * p * r) : 0.0;
        int chunks = countChunks(ref, hyp);
        double penalty = 0.5 * Math.pow(chunks / (double) Math.max(matches, 1), 3);
        return (1.0 - penalty) * fMean;
    }

    private static int countChunks(List<String> ref, List<String> hyp) {
        boolean[] matchedRef = new boolean[ref.size()];
        int[] align = new int[hyp.size()];
        Arrays.fill(align, -1);
        for (int i = 0; i < hyp.size(); i++) {
            for (int j = 0; j < ref.size(); j++) {
                if (!matchedRef[j] && ref.get(j).equals(hyp.get(i))) {
                    align[i] = j;
                    matchedRef[j] = true;
                    break;
                }
            }
        }
        int chunks = 0;
        int prev = -2;
        for (int j : align) {
            if (j >= 0) {
                if (j != prev + 1) {
                    chunks++;
                }
                prev = j;
            }
        }
        return Math.max(chunks, 1);
    }
}
