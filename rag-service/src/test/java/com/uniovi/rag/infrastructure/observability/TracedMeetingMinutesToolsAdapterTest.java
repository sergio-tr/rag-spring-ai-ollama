package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedMeetingMinutesToolsAdapter}.
 */
class TracedMeetingMinutesToolsAdapterTest {

    private RagToolsConfiguration toolsConfig;
    private QueryAnalyser analyser;
    private ObservabilitySupport observability;
    private TracedMeetingMinutesToolsAdapter tracedAdapter;

    @BeforeEach
    void setUp() {
        toolsConfig = mock(RagToolsConfiguration.class);
        analyser = mock(QueryAnalyser.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        tracedAdapter = new TracedMeetingMinutesToolsAdapter(toolsConfig, analyser, observability);
    }

    @Test
    void execute_whenNoTool_returnsNoToolAvailable() {
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(null);

        ToolResult result = tracedAdapter.execute(QueryType.COUNT_DOCUMENTS, "query");

        assertNotNull(result);
        assertTrue(result.result().contains("No tool available"));
        assertEquals("adapter", result.source());
    }

    @Test
    void execute_whenToolPresent_delegatesToSuperAndReturnsResult() {
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.FIND_PARAGRAPH)).thenReturn(mockTool);
        when(analyser.analyse(any())).thenReturn(null);
        when(mockTool.execute(any())).thenReturn(new ToolResult("paragraph", "FindParagraphTool"));

        ToolResult result = tracedAdapter.execute(QueryType.FIND_PARAGRAPH, "find this");

        assertNotNull(result);
        assertEquals("paragraph", result.result());
        assertEquals("FindParagraphTool", result.source());
        verify(mockTool).execute(any());
    }

    @Test
    void execute_whenObservabilityNull_delegatesWithoutTracing() {
        TracedMeetingMinutesToolsAdapter noObs = new TracedMeetingMinutesToolsAdapter(toolsConfig, analyser, null);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(null);

        ToolResult result = noObs.execute(QueryType.COUNT_DOCUMENTS, "q");

        assertNotNull(result);
        assertTrue(result.result().contains("No tool available"));
    }
}
