package com.uniovi.rag.infrastructure.observability;

import io.micrometer.context.ContextSnapshot;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextPropagatingFuturesTest {

    @Test
    void captureContext_returnsSnapshot() {
        ContextSnapshot snap = ContextPropagatingFutures.captureContext();
        assertNotNull(snap);
    }

    @Test
    void withSnapshot_runsSupplierAndRunnable() {
        ContextSnapshot snap = ContextPropagatingFutures.captureContext();
        assertEquals(7, ContextPropagatingFutures.withSnapshot(snap, () -> 7));
        AtomicInteger n = new AtomicInteger();
        ContextPropagatingFutures.withSnapshot(snap, () -> n.set(1));
        assertEquals(1, n.get());
    }

    @Test
    void supplyAsync_and_runAsync_complete() throws Exception {
        assertEquals(42, ContextPropagatingFutures.supplyAsync(() -> 42).get());
        assertNull(ContextPropagatingFutures.runAsync(() -> {}).get());
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            assertEquals(99, ContextPropagatingFutures.supplyAsync(() -> 99, ex).get());
            assertNull(ContextPropagatingFutures.runAsync(() -> {}, ex).get());
        } finally {
            ex.shutdown();
        }
    }
}
