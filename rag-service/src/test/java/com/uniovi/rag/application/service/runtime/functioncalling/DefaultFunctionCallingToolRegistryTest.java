package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultFunctionCallingToolRegistryTest {

    @Test
    void whitelist_hasFiveKinds() {
        DefaultFunctionCallingToolRegistry reg = new DefaultFunctionCallingToolRegistry();
        List<ToolCallback> cbs =
                reg.callbacksFor(
                        List.of(
                                DeterministicToolKind.COUNT_DOCUMENTS_TOOL,
                                DeterministicToolKind.FIND_PARAGRAPH_TOOL,
                                DeterministicToolKind.GET_FIELD_TOOL,
                                DeterministicToolKind.BOOLEAN_QUERY_TOOL,
                                DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL));
        assertEquals(5, cbs.size());
    }

    @Test
    void callbacksPreserveOrderOfRequest() {
        DefaultFunctionCallingToolRegistry reg = new DefaultFunctionCallingToolRegistry();
        var cbs = reg.callbacksFor(List.of(
                DeterministicToolKind.BOOLEAN_QUERY_TOOL,
                DeterministicToolKind.COUNT_DOCUMENTS_TOOL));
        assertEquals("booleanQuery", cbs.getFirst().getName());
        assertEquals("countDocuments", cbs.get(1).getName());
    }
}
