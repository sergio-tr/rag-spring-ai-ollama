package com.uniovi.rag.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexSafetyTest {

    @Test
    void truncateString_null_returnsNull() {
        assertNull(RegexSafety.truncateString(null, 10));
    }

    @Test
    void truncateString_shorterThanMax_returnsSame() {
        assertEquals("ab", RegexSafety.truncateString("ab", 10));
    }

    @Test
    void truncateString_long_truncates() {
        assertEquals("12345", RegexSafety.truncateString("123456789", 5));
    }

    @Test
    void bounded_null_returnsEmpty() {
        assertEquals("", RegexSafety.bounded(null, 5).toString());
    }

    @Test
    void bounded_withinLimit_returnsSameSequence() {
        CharSequence cs = RegexSafety.bounded("hello", 10);
        assertEquals("hello", cs.toString());
    }

    @Test
    void bounded_exceedsLimit_subSequence() {
        assertEquals("12", RegexSafety.bounded("12345", 2).toString());
    }

    @Test
    void matcher_usesBoundedInput() {
        Pattern p = Pattern.compile("ab");
        assertTrue(RegexSafety.matcher(p, "xxabxx", 4).find());
    }
}
