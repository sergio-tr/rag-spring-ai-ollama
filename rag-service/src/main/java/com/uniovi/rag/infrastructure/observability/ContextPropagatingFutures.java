package com.uniovi.rag.infrastructure.observability;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Propagates Micrometer Observation / trace context across thread boundaries.
 * <p>
 * {@link CompletableFuture#supplyAsync} / {@link CompletableFuture#runAsync} on the common
 * {@link java.util.concurrent.ForkJoinPool} and {@link java.util.Collection#parallelStream()}
 * worker threads do not copy those {@link ThreadLocal}s, which produces orphan spans in Jaeger.
 * <p>
 * Use {@link #supplyAsync(Supplier)} / {@link #runAsync(Runnable)} instead of raw CompletableFuture,
 * or for {@code parallelStream()} capture once with {@link #captureContext()} and wrap each worker
 * with {@link #withSnapshot(ContextSnapshot, Supplier)}.
 */
public final class ContextPropagatingFutures {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder()
            .contextRegistry(ContextRegistry.getInstance())
            .build();

    private ContextPropagatingFutures() {
    }

    /**
     * Snapshot of the current thread's observation/trace context; immutable and safe to share
     * with multiple {@code parallelStream()} workers (each calls {@link #withSnapshot} on its own thread).
     */
    public static ContextSnapshot captureContext() {
        return SNAPSHOT_FACTORY.captureAll();
    }

    /**
     * Runs {@code supplier} after restoring {@code snapshot} on the current thread (use inside
     * {@code parallelStream().map/filter} workers).
     */
    public static <T> T withSnapshot(ContextSnapshot snapshot, Supplier<T> supplier) {
        try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
            return supplier.get();
        }
    }

    /**
     * Same as {@link #withSnapshot(ContextSnapshot, Supplier)} for void work.
     */
    public static void withSnapshot(ContextSnapshot snapshot, Runnable runnable) {
        try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
            runnable.run();
        }
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        return CompletableFuture.supplyAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                return supplier.get();
            }
        });
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        return CompletableFuture.supplyAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                return supplier.get();
            }
        }, executor);
    }

    /**
     * Prefer this over {@link CompletableFuture#runAsync(Runnable)} on the common pool.
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        return CompletableFuture.runAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                runnable.run();
            }
        });
    }

    /**
     * Prefer this over {@link CompletableFuture#runAsync(Runnable, Executor)} when the executor
     * does not propagate context (e.g. a plain thread pool).
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        return CompletableFuture.runAsync(() -> {
            try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                runnable.run();
            }
        }, executor);
    }
}
