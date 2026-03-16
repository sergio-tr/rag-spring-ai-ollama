package com.uniovi.rag.tool;

import com.uniovi.rag.model.QueryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ToolRagServiceTest {

    private EmbeddingModel embeddingModel;
    private ToolRagService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        float[] vec = new float[]{0.1f, 0.2f};
        when(embeddingModel.embed(any(String.class))).thenReturn(vec);
        // ToolRagService constructor calls embed(List<String>) with one entry per QueryType in ToolDescriptor
        int n = com.uniovi.rag.configuration.ToolDescriptor.getAll().size();
        List<float[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new float[]{0.1f + i * 0.01f, 0.2f});
        when(embeddingModel.embed(anyList())).thenReturn(list);
        service = new ToolRagService(embeddingModel, 3);
    }

    @Test
    void findTopQueryTypes_nullOrEmptyQuery_returnsFirstK() {
        List<QueryType> top = service.findTopQueryTypes(null, 2);
        assertNotNull(top);
        assertEquals(2, top.size());

        top = service.findTopQueryTypes("   ", 1);
        assertNotNull(top);
        assertEquals(1, top.size());
    }

    @Test
    void findBestQueryType_returnsSingleType() {
        QueryType best = service.findBestQueryType("count documents");
        assertNotNull(best);
        List<QueryType> top = service.findTopQueryTypes("query", 1);
        assertNotNull(top);
        assertEquals(1, top.size());
    }
}
