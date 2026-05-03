package com.uniovi.rag.domain.runtime;

/**
 * Associates the current servlet/request thread with a {@link RagExecutionContext}.
 */
public final class RagExecutionContextHolder {

    private static final ThreadLocal<RagExecutionContext> CURRENT = new ThreadLocal<>();

    private RagExecutionContextHolder() {
    }

    public static void set(RagExecutionContext ctx) {
        CURRENT.set(ctx);
    }

    public static RagExecutionContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
