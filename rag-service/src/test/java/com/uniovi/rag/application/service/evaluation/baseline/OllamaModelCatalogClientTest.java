package com.uniovi.rag.application.service.evaluation.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaModelCatalogClientTest {

    @Test
    void isModelAvailable_whenDaemonUnreachable_returnsFalse() {
        OllamaModelCatalogClient client =
                new OllamaModelCatalogClient(new ObjectMapper(), "http://127.0.0.1:59173");
        assertThat(client.isModelAvailable("any-model:latest")).isFalse();
    }

    @Test
    void isModelAvailable_blankTag_returnsFalse() {
        OllamaModelCatalogClient client =
                new OllamaModelCatalogClient(new ObjectMapper(), "http://127.0.0.1:59173");
        assertThat(client.isModelAvailable("  ")).isFalse();
    }
}
