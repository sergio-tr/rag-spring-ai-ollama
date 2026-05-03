package com.uniovi.rag.infrastructure.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Loggable} default method.
 */
class LoggableTest {

    @Test
    void log_returnsNonNullLogger() {
        Loggable impl = new Impl();
        Logger log = impl.log();
        assertNotNull(log);
        assertTrue(log.getName().contains("Impl") || log.getName().contains("Loggable"));
    }

    private static class Impl implements Loggable {}
}
