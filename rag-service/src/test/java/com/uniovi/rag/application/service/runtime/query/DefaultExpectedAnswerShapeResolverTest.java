package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExpectedAnswerShapeResolverTest {

    private final DefaultExpectedAnswerShapeResolver resolver = new DefaultExpectedAnswerShapeResolver();

    @Test
    void resolve_whenClassifierTypePresent_takesPrecedenceOverEntityHint() {
        EntityExtractionResult entities = entitiesWithHint("list");
        ExpectedAnswerShape out = resolver.resolve(Optional.of(QueryType.BOOLEAN_QUERY), entities);
        assertThat(out).isEqualTo(ExpectedAnswerShape.SCALAR_BOOLEAN);
    }

    @Test
    void resolve_whenHintContainsKnownTokens_mapsExpectedShape() {
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("boolean"))).isEqualTo(ExpectedAnswerShape.SCALAR_BOOLEAN);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("number"))).isEqualTo(ExpectedAnswerShape.SCALAR_COUNT);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("count"))).isEqualTo(ExpectedAnswerShape.SCALAR_COUNT);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("list"))).isEqualTo(ExpectedAnswerShape.LIST);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("decision"))).isEqualTo(ExpectedAnswerShape.DECISION_EXTRACTION);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("field"))).isEqualTo(ExpectedAnswerShape.FIELD_VALUE);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("comparison"))).isEqualTo(ExpectedAnswerShape.COMPARISON);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("text"))).isEqualTo(ExpectedAnswerShape.PARAGRAPH);
    }

    @Test
    void resolve_whenHintMissingOrUnrecognized_returnsUnknown() {
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint(null))).isEqualTo(ExpectedAnswerShape.UNKNOWN);
        assertThat(resolver.resolve(Optional.empty(), entitiesWithHint("something-else"))).isEqualTo(ExpectedAnswerShape.UNKNOWN);
        assertThat(resolver.resolve(Optional.empty(), null)).isEqualTo(ExpectedAnswerShape.UNKNOWN);
    }

    private static EntityExtractionResult entitiesWithHint(String hint) {
        return new EntityExtractionResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.ofNullable(hint),
                Optional.empty(),
                List.of());
    }
}

