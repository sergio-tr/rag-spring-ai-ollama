package com.uniovi.rag.configuration;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDescriptorTest {

    @Test
    void getName() {
        assertEquals("countDocuments", ToolDescriptor.getName(QueryType.COUNT_DOCUMENTS));
        assertEquals("findParagraph", ToolDescriptor.getName(QueryType.FIND_PARAGRAPH));
        assertEquals("extractDecisions", ToolDescriptor.getName(QueryType.DECISION_EXTRACTION));
    }

    @Test
    void getDescription() {
        String desc = ToolDescriptor.getDescription(QueryType.COUNT_DOCUMENTS);
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("Count") || desc.contains("documents"));
    }

    @Test
    void getAll_returnsUnmodifiableMap() {
        Map<QueryType, ToolDescriptor.Descriptor> all = ToolDescriptor.getAll();
        assertNotNull(all);
        assertTrue(all.size() >= 10);
        assertThrows(UnsupportedOperationException.class, () -> all.put(QueryType.COUNT_DOCUMENTS, new ToolDescriptor.Descriptor("x", "y")));
    }

    @Test
    void descriptorRecord() {
        ToolDescriptor.Descriptor d = new ToolDescriptor.Descriptor("name", "desc");
        assertEquals("name", d.name());
        assertEquals("desc", d.description());
    }
}
