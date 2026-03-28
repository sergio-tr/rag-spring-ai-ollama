package com.uniovi.rag.service.analyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpQueryAnalyserTest {

    private final NoOpQueryAnalyser analyser = new NoOpQueryAnalyser();

    @Test
    void analyse_returnsNull() {
        assertNull(analyser.analyse("any query"));
        assertNull(analyser.analyse(null));
        assertNull(analyser.analyse(""));
    }
}
