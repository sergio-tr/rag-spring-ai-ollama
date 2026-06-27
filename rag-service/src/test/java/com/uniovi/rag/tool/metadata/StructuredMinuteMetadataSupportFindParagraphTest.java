package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.Minute;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredMinuteMetadataSupportFindParagraphTest {

    @Test
    void formatFindParagraphTopicEvidenceAnswer_includesSlashDateAndLowercasesBody() {
        Minute minute =
                new Minute(
                        "acta-2-doc",
                        "ACTA 2.pdf",
                        "2025-02-25",
                        "Salón comunitario",
                        null,
                        null,
                        "Antonio Martínez López",
                        "secretary",
                        List.of(),
                        20,
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of("limpieza"),
                        null);

        String answer =
                StructuredMinuteMetadataSupport.formatFindParagraphTopicEvidenceAnswer(
                        "¿Qué se dijo sobre limpieza?",
                        minute,
                        "Se plantea la necesidad de mejorar la limpieza en las zonas comunes.");

        assertThat(answer)
                .isEqualTo(
                        "En el acta del 25/02/2025 se plantea la necesidad de mejorar la limpieza en las zonas comunes.");
    }
}
