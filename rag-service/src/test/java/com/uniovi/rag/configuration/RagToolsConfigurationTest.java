package com.uniovi.rag.configuration;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class RagToolsConfigurationTest {

    @Test
    void getTool_returnsToolForType() {
        Tool countTool = mock(Tool.class);
        Map<QueryType, Tool> map = Map.of(QueryType.COUNT_DOCUMENTS, countTool);
        RagToolsConfiguration config = new RagToolsConfiguration(map);

        assertEquals(countTool, config.getTool(QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void getTool_returnsNullWhenNotPresent() {
        RagToolsConfiguration config = new RagToolsConfiguration(Map.of());
        assertNull(config.getTool(QueryType.FIND_PARAGRAPH));
    }
}
