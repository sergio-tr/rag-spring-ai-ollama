package com.uniovi.rag.application.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentIngestionHumanErrorsTest {

    @Test
    void humanizeMapsKnownPatterns() {
        assertThat(DocumentIngestionHumanErrors.humanize("UNSUPPORTED_TYPE: pdf"))
                .isEqualTo(DocumentIngestionHumanErrors.UNSUPPORTED_FILE);
        assertThat(DocumentIngestionHumanErrors.humanize("Could not parse document"))
                .isEqualTo(DocumentIngestionHumanErrors.PARSE_ERROR);
        assertThat(DocumentIngestionHumanErrors.humanize("embedding service down"))
                .isEqualTo(DocumentIngestionHumanErrors.EMBEDDING_ERROR);
        assertThat(DocumentIngestionHumanErrors.humanize("Document indexing failed: boom"))
                .isEqualTo(DocumentIngestionHumanErrors.INDEX_ERROR);
        assertThat(DocumentIngestionHumanErrors.humanize("Ingestion timed out (watchdog)"))
                .isEqualTo(DocumentIngestionHumanErrors.INGESTION_TIMEOUT);
        assertThat(DocumentIngestionHumanErrors.humanize("FAILED_STALE_INGESTION: stuck"))
                .isEqualTo(DocumentIngestionHumanErrors.INGESTION_TIMEOUT);
        assertThat(DocumentIngestionHumanErrors.humanize("FAILED_EMBEDDING: ollama down"))
                .isEqualTo(DocumentIngestionHumanErrors.EMBEDDING_ERROR);
        assertThat(
                        DocumentIngestionHumanErrors.humanize(
                                "No compatible vector index found for provider=OPENAI_COMPATIBLE"))
                .isEqualTo(DocumentIngestionHumanErrors.INCOMPATIBLE_INDEX_ERROR);
    }
}
