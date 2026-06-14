package com.uniovi.rag.domain.runtime.retrieval;

import java.util.List;
import java.util.Objects;

/** Lexical sparse-query preparation derived from the retrieval query and plan entities. */
public record SparseQueryPreparation(
        String originalQuery,
        String normalizedQuery,
        List<String> keywordTerms,
        List<String> exactPhrases,
        List<String> entityTerms,
        List<String> dateTerms,
        List<String> synonymTerms) {

    public SparseQueryPreparation {
        originalQuery = originalQuery == null ? "" : originalQuery;
        normalizedQuery = normalizedQuery == null ? "" : normalizedQuery;
        keywordTerms = List.copyOf(Objects.requireNonNullElse(keywordTerms, List.of()));
        exactPhrases = List.copyOf(Objects.requireNonNullElse(exactPhrases, List.of()));
        entityTerms = List.copyOf(Objects.requireNonNullElse(entityTerms, List.of()));
        dateTerms = List.copyOf(Objects.requireNonNullElse(dateTerms, List.of()));
        synonymTerms = List.copyOf(Objects.requireNonNullElse(synonymTerms, List.of()));
    }
}
