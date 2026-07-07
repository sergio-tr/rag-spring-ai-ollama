package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActaDocumentAnchorSupportTest {

    @ParameterizedTest(name = "Q10 variant: {0}")
    @CsvSource({
        "resume los puntos tratados en ACTA 3,3",
        "resume los puntos tratados en el acta 3,3",
        "resume los puntos tratados en ACTA 3.pdf,3",
        "resume los puntos tratados en el acta número 3,3",
        "qué decisiones aparecen en acta 3,3"
    })
    void phase45_q10Variants_resolveActaNumber(String query, int expected) {
        assertThat(ActaDocumentAnchorSupport.resolveActaNumber(query)).contains(expected);
        assertThat(ActaDocumentAnchorSupport.canonicalFilename(expected)).isEqualTo("ACTA " + expected + ".pdf");
    }

    @ParameterizedTest(name = "Q10 variant: {0}")
    @CsvSource({
        "¿Qué acuerdo se tomó sobre el ascensor en ACTA 3.pdf?,3",
        "presidente del acta 3,3",
        "¿Cuántos propietarios asistieron en el acta número 3?,3",
        "secretaria de la acta n° 3,3",
        "temas tratados en acta #3,3"
    })
    void q10Variants_resolveActaNumber(String query, int expected) {
        assertThat(ActaDocumentAnchorSupport.resolveActaNumber(query)).contains(expected);
        assertThat(ActaDocumentAnchorSupport.hasExplicitActaDocumentReference(query)).isTrue();
        assertThat(ActaDocumentAnchorSupport.canonicalFilename(expected)).isEqualTo("ACTA " + expected + ".pdf");
    }

    @Test
    void extractActaFilenamesFromText_includesPdfAndBareNumber() {
        assertThat(ActaDocumentAnchorSupport.extractActaFilenamesFromText("Ver ACTA 3.pdf y también acta 6"))
                .containsExactlyInAnyOrder("ACTA 3.pdf", "ACTA 6.pdf");
    }

    @Test
    void preferActaAnchored_keepsOnlyMatchingDocument() {
        RetrievalCandidate acta3 = candidate("ACTA 3.pdf", "Fecha: 25/08/2025");
        RetrievalCandidate acta6 = candidate("ACTA 6.pdf", "Fecha: 25/08/2026");

        List<RetrievalCandidate> filtered =
                ActaDocumentAnchorSupport.preferActaAnchored(List.of(acta6, acta3), 3);

        assertThat(filtered).containsExactly(acta3);
    }

    @Test
    void preferActaAnchored_preservesOrderWhenNoMatch() {
        RetrievalCandidate acta6 = candidate("ACTA 6.pdf", "Fecha: 25/08/2026");
        List<RetrievalCandidate> input = List.of(acta6);

        assertThat(ActaDocumentAnchorSupport.preferActaAnchored(input, 3)).isSameAs(input);
    }

    @Test
    void candidateMatchesActaNumber_byFilenameOrContent() {
        RetrievalCandidate byFilename = candidate("ACTA 3.pdf", "sin número en texto");
        RetrievalCandidate byContent = candidate("acta-3.txt", "Reunión ACTA 3 del 25/08/2025");

        assertThat(ActaDocumentAnchorSupport.candidateMatchesActaNumber(byFilename, 3)).isTrue();
        assertThat(ActaDocumentAnchorSupport.candidateMatchesActaNumber(byContent, 3)).isTrue();
        assertThat(ActaDocumentAnchorSupport.candidateMatchesActaNumber(byFilename, 6)).isFalse();
    }

    @Test
    void dateTargetedQueries_withoutActaNumber_doNotResolve() {
        assertThat(ActaDocumentAnchorSupport.resolveActaNumber(
                        "cuales son los asistentes del acta del 25 de febrero del 2025?"))
                .isEmpty();
    }

    private static RetrievalCandidate candidate(String filename, String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filename", filename);
        return new RetrievalCandidate(
                filename, content, meta, 0.0, 0.0, 1, 0, UUID.randomUUID(), 1.0);
    }
}
