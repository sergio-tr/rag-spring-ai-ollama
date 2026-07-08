package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.util.QueryDateSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * @deprecated Use {@link QueryDateSupport} directly. Retained as a stable import path for query-layer callers.
 */
@Deprecated(since = "2026-07", forRemoval = false)
public final class SpanishQueryDateSupport {

    public static final Pattern LONG_DATE_PHRASE = QueryDateSupport.LONG_DATE_PHRASE;

    public static final Pattern LONG_DATE_PARSE = QueryDateSupport.LONG_DATE_PARSE;

    private SpanishQueryDateSupport() {}

    public static boolean hasLongDateInText(String text) {
        return QueryDateSupport.hasLongSpanishDateInText(text);
    }

    public static List<String> findLongDatePhrases(String text) {
        return QueryDateSupport.findLongDatePhrases(text);
    }

    public static Optional<LocalDate> parseLongDatePhrase(String phrase) {
        return QueryDateSupport.parseLongDatePhrase(phrase);
    }
}
