package com.uniovi.rag.util;

import java.time.LocalDate;

public final class DateParsingSupport {

    private DateParsingSupport() {}

    public static LocalDate parseDateToLocalDate(String dateStr) {
        return QueryDateSupport.parseFlexibleOrNull(dateStr);
    }
}
