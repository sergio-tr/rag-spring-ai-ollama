package com.uniovi.rag.application.service.runtime.traceexport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeTraceExportArtifactTest {

    @Test
    void equals_requiresMatchingBytesAndFields() {
        byte[] a = {1, 2};
        RuntimeTraceExportArtifact x = new RuntimeTraceExportArtifact("f", "t", a, 2, "kind");
        assertThat(x).isEqualTo(x);
        assertThat(x).isNotEqualTo("other");
        assertThat(x).isEqualTo(new RuntimeTraceExportArtifact("f", "t", new byte[] {1, 2}, 2, "kind"));
        assertThat(x).isNotEqualTo(new RuntimeTraceExportArtifact("f", "t", new byte[] {1, 3}, 2, "kind"));
        assertThat(x).isNotEqualTo(new RuntimeTraceExportArtifact("g", "t", a, 2, "kind"));
    }

    @Test
    void hashCode_includesContentHash() {
        byte[] content = {7};
        RuntimeTraceExportArtifact one = new RuntimeTraceExportArtifact("f", "t", content, 1, "k");
        RuntimeTraceExportArtifact same = new RuntimeTraceExportArtifact("f", "t", new byte[] {7}, 1, "k");
        assertThat(one.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void toString_reportsNullContentLength() {
        RuntimeTraceExportArtifact art = new RuntimeTraceExportArtifact("f", "t", null, 0, "k");
        assertThat(art.toString()).contains("content(len=0").contains("exportKind=k");
    }
}
