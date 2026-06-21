package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.Map;

class SparseQueryPreparerTest {

    private SparseQueryPreparer preparer;

    @BeforeEach
    void setUp() {
        preparer = new SparseQueryPreparer(new SparseDomainSynonyms());
    }

    @Test
    void prepare_stripsSpanishStopwordsAndKeepsDomainTerm() {
        var prep =
                preparer.prepare(
                        "¿Cuántas actas mencionan el ascensor?",
                        planWithTopics(List.of("ascensor")));

        assertThat(prep.keywordTerms()).contains("ascensor");
        assertThat(prep.keywordTerms()).doesNotContain("cuantas", "actas", "mencionan");
        assertThat(prep.normalizedQuery()).contains("ascensor");
    }

    @Test
    void prepare_extractsParentheticalPhraseAndSynonymHead() {
        var prep =
                preparer.prepare(
                        "Dime en cuántas reuniones se trató videovigilancia (cámaras de seguridad).",
                        null);

        assertThat(prep.exactPhrases()).contains("cámaras de seguridad");
        assertThat(prep.keywordTerms()).anyMatch(t -> t.toLowerCase().contains("videovigilancia"));
    }

    @Test
    void prepare_foldsAccentsInNormalizedQuery() {
        var prep = preparer.prepare("¿Cuántas veces aparece la calefacción?", null);

        assertThat(prep.normalizedQuery()).contains("calefaccion");
        assertThat(prep.keywordTerms()).anyMatch(t -> t.contains("calefaccion"));
    }

    @Test
    void prepare_recognizesLimpiezaHeadWithoutBroadExpansion() {
        var prep =
                preparer.prepare(
                        "¿Qué se dijo en relación a la limpieza de las zonas comunes?", null);

        assertThat(prep.keywordTerms()).anyMatch(t -> t.toLowerCase().contains("limpieza"));
        assertThat(prep.synonymTerms()).isEmpty();
    }

    @Test
    void prepare_doesNotExpandGasLeakSynonymsForRag019() {
        var prep = preparer.prepare("¿Qué se comentó respecto a la fuga de gas?", null);

        assertThat(prep.synonymTerms()).isEmpty();
        assertThat(prep.keywordTerms()).anyMatch(t -> t.contains("fuga") || t.contains("gas"));
    }

    @Test
    void prepare_preservesYearToken() {
        var prep = preparer.prepare("Número de actas registradas en el año 2028.", null);

        assertThat(prep.dateTerms()).contains("2028");
        assertThat(prep.keywordTerms()).contains("2028");
    }

    @Test
    void prepare_doesNotExpandSynonymsWithoutHeadTerm() {
        var prep = preparer.prepare("¿Qué se comentó respecto a la radiación solar?", null);

        assertThat(prep.synonymTerms()).isEmpty();
    }

    private static QueryPlan planWithTopics(List<String> topics) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        topics,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "q",
                "q",
                "q",
                "q",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }
}
