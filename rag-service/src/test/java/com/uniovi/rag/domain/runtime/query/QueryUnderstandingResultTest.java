package com.uniovi.rag.domain.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryUnderstandingResultTest {

    @Test
    void canonicalConstructor_normalizesOptionals_andCopiesNotesList() {
        NormalizedQuery nq = new NormalizedQuery("raw", "norm", List.of());
        EntityExtractionResult entities = EntityExtractionResult.emptyWithNote("n");
        StructuredRewriteResult rewrite = StructuredRewriteResult.identityDisabled("norm", null);

        List<String> mutableNotes = new java.util.ArrayList<>(List.of("a"));

        QueryUnderstandingResult r =
                new QueryUnderstandingResult(
                        nq,
                        "label",
                        null,
                        ClassifierStatus.OK,
                        entities,
                        rewrite,
                        QueryIntent.UNKNOWN,
                        ExpectedAnswerShape.UNKNOWN,
                        AmbiguityAssessment.sufficient(),
                        mutableNotes);

        assertThat(r.classifierQueryType()).isEmpty();

        mutableNotes.add("b");
        assertThat(r.notes()).containsExactly("a");
    }

    @Test
    void canonicalConstructor_requiresNonNullFields() {
        NormalizedQuery nq = new NormalizedQuery("raw", "norm", List.of());
        EntityExtractionResult entities = EntityExtractionResult.emptyWithNote("n");
        StructuredRewriteResult rewrite = StructuredRewriteResult.identityDisabled("norm", null);

        assertThatThrownBy(
                () -> new QueryUnderstandingResult(
                        null,
                        "label",
                        Optional.of(QueryType.GET_FIELD),
                        ClassifierStatus.OK,
                        entities,
                        rewrite,
                        QueryIntent.UNKNOWN,
                        ExpectedAnswerShape.UNKNOWN,
                        AmbiguityAssessment.sufficient(),
                        List.of()))
                .isInstanceOf(NullPointerException.class);
    }
}

