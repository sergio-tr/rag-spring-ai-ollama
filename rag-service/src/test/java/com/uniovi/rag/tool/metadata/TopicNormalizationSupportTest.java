package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for topic search normalization used by metadata tools and structured acta support. */
class TopicNormalizationSupportTest {

    @Test
    void normalizeTopicSearchText_lowercasesAndStripsAccents() {
        assertThat(AbstractMetadataTool.normalizeTopicSearchText("Videovigilancia"))
                .isEqualTo("videovigilancia");
        assertThat(AbstractMetadataTool.normalizeTopicSearchText("calefacción"))
                .isEqualTo("calefaccion");
        assertThat(AbstractMetadataTool.normalizeTopicSearchText("Fuga de GAS"))
                .isEqualTo("fuga de gas");
    }

    @Test
    void normalizeTopicSearchText_blankInputReturnsEmpty() {
        assertThat(AbstractMetadataTool.normalizeTopicSearchText(null)).isEmpty();
        assertThat(AbstractMetadataTool.normalizeTopicSearchText("")).isEmpty();
        assertThat(AbstractMetadataTool.normalizeTopicSearchText("   ")).isEmpty();
    }

    @Test
    void minuteDiscussesTopicForOccurrence_matchesAccentInsensitiveStemInSummary() {
        var minute =
                new com.uniovi.rag.domain.model.Minute(
                        "id",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.List.of(),
                        19,
                        java.util.Map.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        "Se debatió la instalación de cámaras de videovigilancia en el portal.");
        assertThat(StructuredMinuteMetadataSupport.minuteDiscussesTopicForOccurrence(minute, "videovigilancia"))
                .isTrue();
        assertThat(StructuredMinuteMetadataSupport.minuteDiscussesTopicForOccurrence(minute, "ascensor"))
                .isFalse();
    }
}
