package com.uniovi.rag.infrastructure.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceMdcPopulatingFilterTest {

    @Test
    void doFilterInternal_appliesAndClearsMdc_evenWhenChainSucceeds() throws Exception {
        Tracer tracer = mock(Tracer.class);
        FilterChain chain = mock(FilterChain.class);

        TraceMdcPopulatingFilter f = new TraceMdcPopulatingFilter(tracer);
        f.doFilterInternal(null, null, chain);

        verify(chain).doFilter(null, null);
        assertThat(MDC.get(TraceMdcBridge.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TraceMdcBridge.MDC_SPAN_ID)).isNull();
    }

    @Test
    void doFilterInternal_clearsMdc_evenWhenChainThrows() throws Exception {
        Tracer tracer = mock(Tracer.class);
        FilterChain chain = mock(FilterChain.class);
        RuntimeException boom = new RuntimeException("boom");
        doThrow(boom).when(chain).doFilter(null, null);

        TraceMdcPopulatingFilter f = new TraceMdcPopulatingFilter(tracer);

        try {
            f.doFilterInternal(null, null, chain);
        } catch (RuntimeException e) {
            assertThat(e).isSameAs(boom);
        }

        assertThat(MDC.get(TraceMdcBridge.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TraceMdcBridge.MDC_SPAN_ID)).isNull();
    }
}

