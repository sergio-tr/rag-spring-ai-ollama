package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.SparseQueryPreparation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Builds lexical sparse-query preparation from the retrieval query and query plan. */
@Component
public class SparseQueryPreparer {

    private static final Pattern QUOTED_PHRASE = Pattern.compile("\"([^\"]+)\"|'([^']+)'");
    private static final Pattern PAREN_PHRASE = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern DATE_SLASH = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");
    private static final Pattern DATE_SPANISH =
            Pattern.compile(
                    "\\b\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\s+de\\s+\\d{4}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final SparseDomainSynonyms synonyms;

    public SparseQueryPreparer(SparseDomainSynonyms synonyms) {
        this.synonyms = synonyms;
    }

    public SparseQueryPreparation prepare(String queryText, QueryPlan plan) {
        String original = queryText == null ? "" : queryText.trim();
        String normalized = SpanishRetrievalTextSupport.normalize(original);

        LinkedHashSet<String> exactPhrases = new LinkedHashSet<>();
        extractQuotedPhrases(original, exactPhrases);
        extractParentheticalPhrases(original, exactPhrases);

        LinkedHashSet<String> dateTerms = new LinkedHashSet<>();
        extractDateTerms(original, plan, dateTerms);

        LinkedHashSet<String> entityTerms = new LinkedHashSet<>();
        if (plan != null) {
            addAllNonBlank(entityTerms, plan.targetEntities());
            if (plan.entityExtractionResult() != null) {
                EntityExtractionResult ner = plan.entityExtractionResult();
                addAllNonBlank(entityTerms, ner.people());
                addAllNonBlank(entityTerms, ner.organizations());
                addAllNonBlank(entityTerms, ner.topics());
                addAllNonBlank(entityTerms, ner.locations());
            }
        }

        LinkedHashSet<String> keywordTerms = new LinkedHashSet<>();
        addTokensFromText(normalized, keywordTerms, entityTerms, dateTerms);

        LinkedHashSet<String> presentHeads = new LinkedHashSet<>();
        for (String term : union(keywordTerms, entityTerms, exactPhrases)) {
            String folded = SpanishRetrievalTextSupport.foldAccents(term.toLowerCase(Locale.ROOT).trim());
            if (synonyms.knownHeads().contains(folded)) {
                presentHeads.add(folded);
            }
        }
        LinkedHashSet<String> synonymTerms = new LinkedHashSet<>(synonyms.expandWhenHeadPresent(presentHeads));

        return new SparseQueryPreparation(
                original,
                normalized,
                List.copyOf(keywordTerms),
                List.copyOf(exactPhrases),
                List.copyOf(entityTerms),
                List.copyOf(dateTerms),
                List.copyOf(synonymTerms));
    }

    public SparseQueryPreparation prepare(String queryText) {
        return prepare(queryText, null);
    }

    private static void extractQuotedPhrases(String original, Set<String> out) {
        Matcher m = QUOTED_PHRASE.matcher(original);
        while (m.find()) {
            String phrase = m.group(1) != null ? m.group(1) : m.group(2);
            if (phrase != null && !phrase.isBlank()) {
                out.add(phrase.trim());
            }
        }
    }

    private static void extractParentheticalPhrases(String original, Set<String> out) {
        Matcher m = PAREN_PHRASE.matcher(original);
        while (m.find()) {
            String phrase = m.group(1);
            if (phrase != null && !phrase.isBlank()) {
                out.add(phrase.trim());
            }
        }
    }

    private static void extractDateTerms(String original, QueryPlan plan, Set<String> out) {
        if (plan != null && plan.entityExtractionResult() != null) {
            addAllNonBlank(out, plan.entityExtractionResult().dates());
        }
        Matcher year = YEAR.matcher(original);
        while (year.find()) {
            out.add(year.group());
        }
        Matcher slash = DATE_SLASH.matcher(original);
        while (slash.find()) {
            out.add(slash.group());
        }
        Matcher spanish = DATE_SPANISH.matcher(original);
        while (spanish.find()) {
            out.add(spanish.group());
        }
    }

    private static void addTokensFromText(
            String normalized,
            Set<String> keywordTerms,
            Set<String> entityTerms,
            Set<String> dateTerms) {
        if (normalized.isBlank()) {
            return;
        }
        Set<String> protectedTerms = new LinkedHashSet<>();
        for (String e : entityTerms) {
            protectedTerms.add(SpanishRetrievalTextSupport.foldAccents(e.toLowerCase(Locale.ROOT).trim()));
        }
        for (String d : dateTerms) {
            protectedTerms.add(SpanishRetrievalTextSupport.foldAccents(d.toLowerCase(Locale.ROOT).trim()));
        }
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String folded = SpanishRetrievalTextSupport.foldAccents(token.toLowerCase(Locale.ROOT).trim());
            if (protectedTerms.contains(folded)) {
                keywordTerms.add(token);
                continue;
            }
            if (SpanishRetrievalTextSupport.isSignificantToken(token)) {
                keywordTerms.add(token);
            }
        }
    }

    @SafeVarargs
    private static LinkedHashSet<String> union(Set<String>... sets) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Set<String> s : sets) {
            if (s != null) {
                out.addAll(s);
            }
        }
        return out;
    }

    private static void addAllNonBlank(Set<String> out, List<String> values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
    }
}
