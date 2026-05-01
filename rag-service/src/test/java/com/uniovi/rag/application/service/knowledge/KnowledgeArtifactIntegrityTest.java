package com.uniovi.rag.application.service.knowledge;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeArtifactIntegrityTest {

    @Test
    void sha256Hex_isDeterministicForOrderedMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("text", "hello");
        String h1 = ArtifactPayloadHasher.sha256Hex(payload);
        String h2 = ArtifactPayloadHasher.sha256Hex(payload);
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }
}
