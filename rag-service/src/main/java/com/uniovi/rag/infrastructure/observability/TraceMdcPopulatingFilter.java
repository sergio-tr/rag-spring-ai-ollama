package com.uniovi.rag.infrastructure.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures {@link org.slf4j.MDC} carries {@code traceId} / {@code spanId} from the active Micrometer span
 * for the servlet request (after the observation/tracing filters have started a span).
 */
@Component
@ConditionalOnBean(Tracer.class)
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class TraceMdcPopulatingFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceMdcPopulatingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            TraceMdcBridge.apply(tracer);
            filterChain.doFilter(request, response);
        } finally {
            TraceMdcBridge.clear();
        }
    }
}
