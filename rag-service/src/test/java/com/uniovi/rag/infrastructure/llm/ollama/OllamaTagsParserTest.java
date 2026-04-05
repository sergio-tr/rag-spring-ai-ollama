package com.uniovi.rag.infrastructure.llm.ollama;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaTagsParserTest {

    @Test
    void parseModelNames_extractsNames() {
        String json = """
                {"models":[{"name":"gemma3:4b","size":123},{"name":"mxbai-embed-large","size":456}]}
                """;
        Set<String> names = OllamaTagsParser.parseModelNames(json);
        assertEquals(2, names.size());
        assertTrue(names.contains("gemma3:4b"));
        assertTrue(names.contains("mxbai-embed-large"));
    }

    @Test
    void parseModelNames_emptyModelsArray() {
        Set<String> names = OllamaTagsParser.parseModelNames("{\"models\":[]}");
        assertTrue(names.isEmpty());
    }

    @Test
    void parseModelNames_missingModelsKey() {
        Set<String> names = OllamaTagsParser.parseModelNames("{}");
        assertTrue(names.isEmpty());
    }

    @Test
    void parseModelNames_skipsNonObjectEntries() {
        String json = "{\"models\":[{\"name\":\"ok\"},{\"bad\":1},null,{\"name\":\"z\"}]}";
        Set<String> names = OllamaTagsParser.parseModelNames(json);
        assertEquals(2, names.size());
        assertTrue(names.contains("ok"));
        assertTrue(names.contains("z"));
    }
}
