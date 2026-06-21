package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParentAnswerFingerprintTest {

    @Test
    void sha256Hex_isDeterministicForSameText() {
        String hash1 = ParentAnswerFingerprint.sha256Hex("parent answer");
        String hash2 = ParentAnswerFingerprint.sha256Hex("parent answer");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256Hex_differsForDifferentText() {
        assertThat(ParentAnswerFingerprint.sha256Hex("a"))
                .isNotEqualTo(ParentAnswerFingerprint.sha256Hex("b"));
    }
}
