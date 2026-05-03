package com.uniovi.rag.interfaces.rest.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserFacingErrorSanitizerTest {

    @Test
    void sanitize_empty_returnsEmpty() {
        assertEquals("", UserFacingErrorSanitizer.sanitize(null, 80));
        assertEquals("", UserFacingErrorSanitizer.sanitize("  ", 80));
    }

    @Test
    void sanitize_proxyHtml_returnsEmpty() {
        assertEquals("", UserFacingErrorSanitizer.sanitize("<html><body>502</body></html>", 80));
        assertEquals("", UserFacingErrorSanitizer.sanitize("<!DOCTYPE html><html>", 80));
    }

    @Test
    void sanitize_stackLike_returnsEmpty() {
        String stack =
                """
                Exception: boom
                \tat foo(Foo.java:10)
                \tat bar(Foo.java:20)
                """;
        assertEquals("", UserFacingErrorSanitizer.sanitize(stack, 200));
    }

    @Test
    void sanitize_plain_truncates() {
        String in = "x".repeat(100);
        assertEquals(51, UserFacingErrorSanitizer.sanitize(in, 50).length());
        assertTrue(UserFacingErrorSanitizer.sanitize(in, 50).endsWith("…"));
    }

    @Test
    void sanitizeOrDefault_usesFallbackWhenStripped() {
        assertEquals(
                "fallback",
                UserFacingErrorSanitizer.sanitizeOrDefault("<html>x</html>", 80, "fallback"));
    }
}
