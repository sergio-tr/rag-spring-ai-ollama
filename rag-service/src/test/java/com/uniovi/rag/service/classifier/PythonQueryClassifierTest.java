package com.uniovi.rag.service.classifier;

import com.uniovi.rag.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PythonQueryClassifierTest {

    @Test
    void noArgConstructor_classifyReturnsNull() {
        PythonQueryClassifier c = new PythonQueryClassifier();
        assertNull(c.classify("any query"));
        assertNull(c.classifyWithText("any query"));
    }

    @Test
    void emptyScriptPath_classifyReturnsNull() {
        PythonQueryClassifier c = new PythonQueryClassifier("", "");
        assertNull(c.classify("query"));
        assertNull(c.classifyWithText("query"));
    }

    @Test
    void nonExistentScriptPath_classifyReturnsNull() {
        PythonQueryClassifier c = new PythonQueryClassifier("", "/nonexistent/path/classify_question.py");
        assertNull(c.classify("query"));
    }

    @Test
    void nullScriptPath_treatedAsEmpty() {
        PythonQueryClassifier c = new PythonQueryClassifier(null, null);
        assertNull(c.classify("q"));
    }
}
