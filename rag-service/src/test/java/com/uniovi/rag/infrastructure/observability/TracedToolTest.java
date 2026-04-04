package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedTool}.
 */
class TracedToolTest {

    private Tool delegate;
    private ObservabilitySupport observability;
    private TracedTool tracedTool;

    @BeforeEach
    void setUp() {
        delegate = mock(Tool.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        tracedTool = new TracedTool(delegate, observability);
    }

    @Test
    void execute_delegatesAndReturnsResult() {
        ToolExecutionContext ctx = ToolExecutionContext.of("query", QueryType.COUNT_DOCUMENTS);
        ToolResult expected = new ToolResult("5", "CountDocumentsTool");
        when(delegate.execute(any(ToolExecutionContext.class))).thenReturn(expected);

        ToolResult result = tracedTool.execute(ctx);

        assertEquals(expected, result);
        verify(delegate).execute(ctx);
    }

    @Test
    void execute_whenObservabilityNull_delegatesOnly() {
        TracedTool noObs = new TracedTool(delegate, null);
        ToolResult expected = new ToolResult("x", "source");
        when(delegate.execute(any(ToolExecutionContext.class))).thenReturn(expected);

        ToolResult result = noObs.execute(ToolExecutionContext.of("q"));

        assertEquals(expected, result);
        verify(delegate).execute(any(ToolExecutionContext.class));
    }

    @Test
    void execute_nullContext_doesNotThrow() {
        when(delegate.execute(null)).thenReturn(new ToolResult("ok", "s"));

        ToolResult result = tracedTool.execute(null);

        assertNotNull(result);
        assertEquals("ok", result.result());
        verify(delegate).execute(null);
    }
}
