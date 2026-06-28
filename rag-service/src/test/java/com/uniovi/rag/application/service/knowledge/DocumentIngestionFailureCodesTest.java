package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentIngestionFailureCodesTest {

    @Test
    void format_joinsCodeAndDetail() {
        assertThat(DocumentIngestionFailureCodes.format("FAILED_PARSING", "bad pdf"))
                .isEqualTo("FAILED_PARSING: bad pdf");
        assertThat(DocumentIngestionFailureCodes.format("FAILED_GENERIC", null))
                .isEqualTo("FAILED_GENERIC");
    }

    @Test
    void classify_mapsKnownPatterns() {
        assertThat(DocumentIngestionFailureCodes.classify(new RuntimeException("embedding service down")))
                .startsWith(DocumentIngestionFailureCodes.FAILED_EMBEDDING);
        assertThat(DocumentIngestionFailureCodes.classify(new RuntimeException("Could not parse document")))
                .startsWith(DocumentIngestionFailureCodes.FAILED_PARSING);
        assertThat(DocumentIngestionFailureCodes.classify(new RuntimeException("DUPLICATE_FILE")))
                .startsWith(DocumentIngestionFailureCodes.DUPLICATE);
        assertThat(DocumentIngestionFailureCodes.classify(new RuntimeException("Ingestion timed out")))
                .startsWith(DocumentIngestionFailureCodes.FAILED_TIMEOUT);
    }
}
