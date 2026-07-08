package com.uniovi.rag.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.DateGroundingSupport;
import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.application.service.runtime.query.guard.QueryDateExtractor;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression matrix for real user date phrasings across detection, extraction, parsing, and grounding.
 */
class QueryDatePhrasingRegressionTest {

  private final QueryDateExtractor extractor = new QueryDateExtractor();

  static Stream<Arguments> dayPrecisionQueries() {
    return Stream.of(
        Arguments.of(
            "dime los asistentes del acta del 25 de febrero del 2025",
            "2025-02-25"),
        Arguments.of(
            "¿Quiénes fueron los asistentes del acta del 24 de febrero de 2025?",
            "2025-02-24"),
        Arguments.of(
            "cuales son los asistentes del acta del 25 de agosto del 2025?",
            "2025-08-25"),
        Arguments.of(
            "¿Quién fue el presidente del acta del 24/02/2025?",
            "2025-02-24"),
        Arguments.of(
            "hazme un resumen del acta del 25/02/26",
            "2026-02-25"),
        Arguments.of(
            "Duración de la reunión del 25 de febrero de 2026.",
            "2026-02-25"),
        Arguments.of(
            "Enumera todos los asistentes a la reunión del 25/02/2025.",
            "2025-02-25"),
        Arguments.of(
            "25 febrero 2025",
            "2025-02-25"));
  }

  @ParameterizedTest
  @MethodSource("dayPrecisionQueries")
  void userPhrasing_isDetectedExtractedParsedAndGrounded(String query, String expectedIso) {
    assertThat(QueryDateSupport.hasParseableDateInText(query)).isTrue();
    assertThat(ActaFieldAnchorHeuristics.hasExplicitDateInText(query)).isTrue();
    assertThat(QueryDateSupport.firstNormalizedIsoDateInText(query)).contains(expectedIso);
    assertThat(extractor.extractNormalizedDate(query, null)).isEqualTo(expectedIso);
    assertThat(DateGroundingSupport.requestedDate(query))
        .isPresent()
        .get()
        .extracting(DateGroundingSupport.RequestedDate::value)
        .isEqualTo(expectedIso);
  }

  @ParameterizedTest
  @MethodSource("dayPrecisionQueries")
  void extractedCandidates_parseToSameDay(String query, String expectedIso) {
    LocalDate expected = LocalDate.parse(expectedIso);
    assertThat(
            QueryDateSupport.extractDateCandidatesFromText(query).stream()
                .map(QueryDateSupport::parseFlexibleOrNull)
                .filter(d -> d != null)
                .anyMatch(expected::equals))
        .isTrue();
  }
}
