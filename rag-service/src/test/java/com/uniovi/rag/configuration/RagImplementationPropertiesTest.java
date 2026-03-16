package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagImplementationPropertiesTest {

    @Test
    void defaults() {
        RagImplementationProperties p = new RagImplementationProperties();
        assertEquals("process", p.getQueryServiceImpl());
        assertEquals("basic", p.getRetrieverImpl());
        assertEquals("minute-ner", p.getAnalyserImpl());
    }

    @Test
    void setters() {
        RagImplementationProperties p = new RagImplementationProperties();
        p.setQueryServiceImpl("simple");
        p.setRetrieverImpl("filtered");
        p.setAnalyserImpl("no-op");
        assertEquals("simple", p.getQueryServiceImpl());
        assertEquals("filtered", p.getRetrieverImpl());
        assertEquals("no-op", p.getAnalyserImpl());
    }
}
