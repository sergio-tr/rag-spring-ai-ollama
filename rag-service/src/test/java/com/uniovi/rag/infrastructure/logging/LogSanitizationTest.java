package com.uniovi.rag.infrastructure.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizationTest {

    @Test
    void singleLineForLog_passesThroughPlainIds() {
        assertThat(LogSanitization.singleLineForLog("doc-1")).isEqualTo("doc-1");
    }

    @Test
    void singleLineForLog_replacesLineBreaks() {
        assertThat(LogSanitization.singleLineForLog("a\rb\nc"))
                .doesNotContain("\n")
                .doesNotContain("\r");
    }

    @Test
    void singleLineForLog_nullBecomesNullLiteral() {
        assertThat(LogSanitization.singleLineForLog(null)).isEqualTo("null");
    }
}
