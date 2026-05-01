package com.uniovi.rag.application.service.runtime.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZipExportArtifactSupportTest {

    @Test
    void sameArtifact_matchesEqualPayloads() {
        byte[] bytes = {1, 2, 3};
        assertThat(ZipExportArtifactSupport.sameArtifact("f.zip", "application/zip", bytes, 3, "f.zip", "application/zip", bytes, 3))
                .isTrue();
    }

    @Test
    void sameArtifact_rejectsSizeMismatch() {
        byte[] bytes = {1};
        assertThat(ZipExportArtifactSupport.sameArtifact("f", "t", bytes, 1, "f", "t", bytes, 2))
                .isFalse();
    }

    @Test
    void sameArtifact_rejectsContentMismatch() {
        assertThat(ZipExportArtifactSupport.sameArtifact(
                        "f", "t", new byte[] {1}, 1, "f", "t", new byte[] {2}, 1))
                .isFalse();
    }

    @Test
    void artifactHash_stableForEqualInputs() {
        byte[] content = {9, 9};
        int h1 = ZipExportArtifactSupport.artifactHash("a.zip", "z", content, 2);
        int h2 = ZipExportArtifactSupport.artifactHash("a.zip", "z", content, 2);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void artifactToString_handlesNullContent() {
        String s = ZipExportArtifactSupport.artifactToString("T", "f", "m", null, 0);
        assertThat(s).contains("T[").contains("filename=f").contains("content(len=0");
    }
}
