package com.uniovi.rag.application.service.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.knowledge.document.DocumentRepresentationBuilder.Representation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentRepresentationBuilderTest {

    private static String loadFixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/acta-fixtures/" + name), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> actaMetadata(int attendeeCount, List<String> attendees) {
        Map<String, Object> acta = new LinkedHashMap<>();
        acta.put("date_iso", "2025-02-24");
        acta.put("date", "24 de febrero de 2025");
        acta.put("president", "Juan Pérez Gutiérrez");
        acta.put("secretary", "Rosa Aguilar Fernández");
        acta.put("numberOfAttendees", attendeeCount);
        acta.put("attendees", attendees);
        acta.put(
                "topics",
                List.of(
                        "Lectura y aprobación del acta anterior",
                        "Estado de cuentas y presupuesto anual",
                        "Reparaciones y mantenimiento del edificio"));
        return acta;
    }

    // 1. DOC_LEVEL not just first chunk: tail-only content must survive into the representation.
    @Test
    void build_longContent_keepsTailContentNotJustFirstChars() throws IOException {
        String content = loadFixture("acta-1.txt");
        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", null, false, 400);

        assertThat(rep.truncated()).isTrue();
        assertThat(rep.text().length()).isLessThanOrEqualTo(400);
        // The old first-400-chars-only bug cut before the closing line; the balanced builder must
        // surface at least some tail material (the meeting's closing sentence) too.
        assertThat(rep.text()).contains("20: 30");
    }

    // 2. DOC_LEVEL head/middle/tail coverage.
    @Test
    void build_longContent_coversHeadAndTailOfDocument() throws IOException {
        String content = loadFixture("acta-1.txt");
        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", null, false, 600);

        assertThat(rep.text()).contains("ACTA DE LA REUNIÓN"); // head
        assertThat(rep.text()).contains("No habiendo más asuntos"); // tail
    }

    // 3. DOC_LEVEL metadata=true includes metadata.
    @Test
    void build_metadataEnabled_prependsActaMetadataBlock() throws IOException {
        String content = loadFixture("acta-1.txt");
        Map<String, Object> acta = actaMetadata(20, List.of("Juan Pérez Gutiérrez", "Rosa Aguilar Fernández"));

        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, 1740);

        assertThat(rep.text()).contains("2025-02-24");
        assertThat(rep.text()).contains("Presidente: Juan Pérez Gutiérrez");
        assertThat(rep.text()).contains("Asistentes");
        assertThat(rep.text()).contains("Archivo: ACTA 1.pdf");
    }

    // 4. HYBRID doc-level uses the same strategy (builder is strategy-agnostic; same call, same output).
    @Test
    void build_isDeterministic_forSameInputsRegardlessOfCallSite() throws IOException {
        String content = loadFixture("acta-1.txt");
        Map<String, Object> acta = actaMetadata(20, List.of("Juan Pérez Gutiérrez"));

        Representation documentLevel = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, 1740);
        Representation hybridDocTail = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, 1740);

        assertThat(hybridDocTail.text()).isEqualTo(documentLevel.text());
        assertThat(hybridDocTail.truncated()).isEqualTo(documentLevel.truncated());
    }

    // 5. Untouched by this fix: CHUNK_LEVEL never calls this builder (covered structurally in
    // KnowledgeIndexingServiceTest — CHUNK_LEVEL keeps using IndexingEmbeddingGuard.prepareForEmbedding
    // directly). This test only pins the builder's own "short input passthrough" behavior, which chunk
    // callers would get too if they ever used it (they don't).
    @Test
    void build_contentWithinBudget_isReturnedUnmodified() {
        Representation rep = DocumentRepresentationBuilder.build("short body text", "f.pdf", null, false, 400);

        assertThat(rep.text()).isEqualTo("short body text");
        assertThat(rep.truncated()).isFalse();
    }

    // 6. metadata=false: no metadata fields exposed in the representation text.
    @Test
    void build_metadataDisabled_neverIncludesActaFields() throws IOException {
        String content = loadFixture("acta-1.txt");
        Map<String, Object> acta = actaMetadata(20, List.of("Juan Pérez Gutiérrez", "Rosa Aguilar Fernández"));

        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, false, 400);

        assertThat(rep.text()).doesNotContain("Presidente:");
        assertThat(rep.text()).doesNotContain("Secretario:");
        assertThat(rep.text()).doesNotContain("Asistentes");
        assertThat(rep.text()).doesNotContain("Archivo:");
    }

    // 7. metadata=true preserves metadata (present across a range of budgets, not just one lucky size).
    @Test
    void build_metadataEnabled_preservesActaFieldsAcrossBudgets() throws IOException {
        String content = loadFixture("acta-1.txt");
        Map<String, Object> acta = actaMetadata(20, List.of("Juan Pérez Gutiérrez", "Rosa Aguilar Fernández"));

        for (int budget : new int[] {500, 900, 1740}) {
            Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, budget);
            assertThat(rep.text()).as("budget=%d", budget).contains("2025-02-24");
        }
    }

    // 8. Long attendee lists not cut mid-list if budget allows; cleanly summarized (not mid-name cut)
    // when the budget is tight.
    @Test
    void build_attendeesList_fullyIncludedWhenBudgetAllows() throws IOException {
        String content = loadFixture("acta-1.txt");
        List<String> attendees =
                List.of(
                        "Juan Pérez Gutiérrez", "Marta González Ramírez", "Luis Ramírez Ortega", "Ana Sánchez Herrera",
                        "Roberto Martínez Vázquez", "Carmen Herrera Jiménez", "Pedro Jiménez Suárez",
                        "Laura Díaz Castro", "Manuel Ortega Medina", "Isabel Castro Torres", "Jorge Moreno Navarro",
                        "Beatriz Suárez Aguilar", "Alejandro Torres Rojas", "Natalia Vázquez Gutiérrez",
                        "Eduardo Rojas Martínez", "Silvia Medina Pérez", "Ricardo Flores Sánchez",
                        "Patricia Navarro Díaz", "Daniel Gutiérrez Moreno", "Rosa Aguilar Fernández");
        Map<String, Object> acta = actaMetadata(20, attendees);

        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, 1740);

        for (String attendee : attendees) {
            assertThat(rep.text()).as("attendee list should be complete").contains(attendee);
        }
        assertThat(rep.text()).doesNotContain("more)");
    }

    @Test
    void build_attendeesList_cutAtItemBoundary_notMidName_whenBudgetTight() throws IOException {
        String content = loadFixture("acta-1.txt");
        List<String> attendees =
                List.of(
                        "Juan Pérez Gutiérrez", "Marta González Ramírez", "Luis Ramírez Ortega", "Ana Sánchez Herrera",
                        "Roberto Martínez Vázquez", "Carmen Herrera Jiménez", "Pedro Jiménez Suárez",
                        "Laura Díaz Castro", "Manuel Ortega Medina", "Isabel Castro Torres", "Jorge Moreno Navarro",
                        "Beatriz Suárez Aguilar", "Alejandro Torres Rojas", "Natalia Vázquez Gutiérrez",
                        "Eduardo Rojas Martínez", "Silvia Medina Pérez", "Ricardo Flores Sánchez",
                        "Patricia Navarro Díaz", "Daniel Gutiérrez Moreno", "Rosa Aguilar Fernández");
        Map<String, Object> acta = actaMetadata(20, attendees);

        Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", acta, true, 260);

        assertThat(rep.truncated()).isTrue();
        // Every attendee name that DOES appear must appear whole (never a fragment of a name).
        for (String attendee : attendees) {
            if (rep.text().contains(attendee.substring(0, 4))) {
                boolean fullNamePresent = rep.text().contains(attendee);
                boolean notMentionedAtAll = !rep.text().contains(attendee.substring(0, attendee.length() - 3));
                assertThat(fullNamePresent || notMentionedAtAll)
                        .as("attendee '%s' should be whole or entirely absent, not a fragment", attendee)
                        .isTrue();
            }
        }
    }

    // 9. Respects embedding max without first-only truncation: representation must stay within the
    // ceiling for a range of realistic ceilings, and must not be a naive head-only substring for a
    // ceiling well below the document length.
    @Test
    void build_respectsMaxChars_forVariousCeilings() throws IOException {
        String content = loadFixture("acta-1.txt");
        for (int max : new int[] {100, 250, 400, 800, 1200}) {
            Representation rep = DocumentRepresentationBuilder.build(content, "ACTA 1.pdf", null, false, max);
            assertThat(rep.text().length()).as("max=%d", max).isLessThanOrEqualTo(max);
        }
    }

    @Test
    void build_documentShorterThanBudget_isNotTruncated() {
        String content = "Acta corta con poco contenido.";
        Representation rep = DocumentRepresentationBuilder.build(content, "f.pdf", null, false, 1740);

        assertThat(rep.truncated()).isFalse();
        assertThat(rep.text()).isEqualTo(content);
    }

    @Test
    void build_nullContent_returnsEmptyNotTruncated() {
        Representation rep = DocumentRepresentationBuilder.build(null, "f.pdf", null, false, 400);

        assertThat(rep.text()).isEmpty();
        assertThat(rep.truncated()).isFalse();
    }
}
