package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.Minute;
import java.util.List;
import java.util.Map;
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
                new Minute(
                        "id",
                        "ACTA 6.pdf",
                        "2026-08-25",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        19,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "Se debatió la instalación de cámaras de videovigilancia en el portal.");
        assertThat(StructuredMinuteMetadataSupport.minuteDiscussesTopicForOccurrence(minute, "videovigilancia"))
                .isTrue();
        assertThat(StructuredMinuteMetadataSupport.minuteDiscussesTopicForOccurrence(minute, "ascensor"))
                .isFalse();
    }
}
