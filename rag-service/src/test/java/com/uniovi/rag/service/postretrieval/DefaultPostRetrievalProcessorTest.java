package com.uniovi.rag.service.postretrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPostRetrievalProcessorTest {

    @Test
    void whenNullDocuments_returnsNull() {
        DefaultPostRetrievalProcessor processor = new DefaultPostRetrievalProcessor(3);
        assertNull(processor.process(null, "query"));
    }

    @Test
    void whenSizeLessThanOrEqualTopK_returnsSameList() {
        DefaultPostRetrievalProcessor processor = new DefaultPostRetrievalProcessor(5);
        List<Document> docs = List.of(
                new Document("a", Map.of()),
                new Document("b", Map.of())
        );
        List<Document> result = processor.process(docs, "q");
        assertSame(docs, result);
        assertEquals(2, result.size());
    }

    @Test
    void whenSizeGreaterThanTopK_returnsSublist() {
        DefaultPostRetrievalProcessor processor = new DefaultPostRetrievalProcessor(2);
        List<Document> docs = List.of(
                new Document("a", Map.of()),
                new Document("b", Map.of()),
                new Document("c", Map.of())
        );
        List<Document> result = processor.process(docs, "q");
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getContent());
        assertEquals("b", result.get(1).getContent());
    }

    @Test
    void constructorEnforcesMinTopKOne() {
        DefaultPostRetrievalProcessor processor = new DefaultPostRetrievalProcessor(0);
        List<Document> docs = List.of(new Document("x", Map.of()));
        List<Document> result = processor.process(docs, "q");
        assertEquals(1, result.size());
    }
}
