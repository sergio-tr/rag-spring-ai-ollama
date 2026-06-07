package com.uniovi.rag.tool;

import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingMinutesToolsAdapterTest {

    private RagToolsConfiguration toolsConfig;
    private QueryAnalyser analyser;
    private MeetingMinutesToolsAdapter adapter;

    @BeforeEach
    void setUp() {
        toolsConfig = mock(RagToolsConfiguration.class);
        analyser = mock(QueryAnalyser.class);
        adapter = new MeetingMinutesToolsAdapter(toolsConfig, analyser);
    }

    @Test
    void execute_whenNoToolForType_returnsNoToolAvailableMessage() {
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(null);

        ToolResult result = adapter.execute(QueryType.COUNT_DOCUMENTS, "¿Cuántos documentos?");

        assertNotNull(result);
        assertTrue(result.result().contains("No tool available"));
        assertEquals("adapter", result.source());
    }

    @Test
    void execute_whenToolPresent_delegatesToTool() {
        Tool mockTool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.FIND_PARAGRAPH)).thenReturn(mockTool);
        when(analyser.analyse(anyString())).thenReturn(null);
        when(mockTool.execute(any(ToolExecutionContext.class)))
                .thenReturn(new ToolResult("paragraph content", "FindParagraphTool"));

        ToolResult result = adapter.execute(QueryType.FIND_PARAGRAPH, "find paragraph");

        assertNotNull(result);
        assertEquals("paragraph content", result.result());
        assertEquals("FindParagraphTool", result.source());
        verify(mockTool).execute(any(ToolExecutionContext.class));
    }

    @Test
    void countDocuments_delegatesToRun() {
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(null);
        String r = adapter.countDocuments("query");
        assertNotNull(r);
        assertTrue(r.contains("No tool available") || r.isEmpty());
    }
}
