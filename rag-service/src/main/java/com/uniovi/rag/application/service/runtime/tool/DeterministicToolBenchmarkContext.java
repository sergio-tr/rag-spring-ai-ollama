package com.uniovi.rag.application.service.runtime.tool;

import java.util.Optional;

/**
 * Thread-local Lab benchmark scope for deterministic routing oracle signals.
 * Set only from evaluation batch drivers; never from product chat.
 */
public final class DeterministicToolBenchmarkContext {

    private static final ThreadLocal<Boolean> ROUTING_ORACLE_ENABLED = new ThreadLocal<>();
    private static final ThreadLocal<String> EXPECTED_QUERY_TYPE = new ThreadLocal<>();

    private DeterministicToolBenchmarkContext() {}

    public static void setRoutingOracleEnabled(boolean enabled) {
        if (enabled) {
            ROUTING_ORACLE_ENABLED.set(Boolean.TRUE);
        } else {
            ROUTING_ORACLE_ENABLED.remove();
        }
    }

    public static boolean routingOracleEnabled() {
        return Boolean.TRUE.equals(ROUTING_ORACLE_ENABLED.get());
    }

    public static void setExpectedQueryType(String queryType) {
        if (queryType == null || queryType.isBlank()) {
            EXPECTED_QUERY_TYPE.remove();
        } else {
            EXPECTED_QUERY_TYPE.set(queryType.trim());
        }
    }

    public static Optional<String> expectedQueryType() {
        return Optional.ofNullable(EXPECTED_QUERY_TYPE.get()).filter(s -> !s.isBlank());
    }

    public static void clearItemScope() {
        EXPECTED_QUERY_TYPE.remove();
    }

    public static void clearRunScope() {
        ROUTING_ORACLE_ENABLED.remove();
        EXPECTED_QUERY_TYPE.remove();
    }

    public static AutoCloseable openRun(boolean routingOracleEnabled) {
        setRoutingOracleEnabled(routingOracleEnabled);
        return DeterministicToolBenchmarkContext::clearRunScope;
    }

    public static AutoCloseable openItem(String expectedQueryType) {
        setExpectedQueryType(expectedQueryType);
        return DeterministicToolBenchmarkContext::clearItemScope;
    }
}
