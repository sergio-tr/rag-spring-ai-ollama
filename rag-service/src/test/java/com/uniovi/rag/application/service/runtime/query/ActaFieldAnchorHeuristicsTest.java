package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import org.junit.jupiter.api.Test;

class ActaFieldAnchorHeuristicsTest {

    @Test
    void fdFl03_compoundAugustVideovigilanciaFilter_doesNotNeedActaAnchor() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        assertThat(ActaFieldAnchorHeuristics.needsActaAnchor(query, EntityExtractionResult.emptyWithNote(null)))
                .as("FD-FL-03: month + topic + attendee threshold must not trigger clarification anchor")
                .isFalse();
    }

    @Test
    void fdFl03_compoundAugustVideovigilanciaFilter_isCorpusWideAggregate() {
        String query =
                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?";
        assertThat(ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(query.toLowerCase()))
                .isTrue();
    }

    @Test
    void undatedParticipantCount_stillNeedsAnchor() {
        assertThat(ActaFieldAnchorHeuristics.isUndatedParticipantCount("¿Cuántos participantes asistieron?"))
                .isTrue();
    }

    @Test
    void fdCe02_exactAttendeeListing_doesNotNeedActaAnchor() {
        String query =
                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(query)).isTrue();
        assertThat(ActaFieldAnchorHeuristics.needsActaAnchor(query, EntityExtractionResult.emptyWithNote(null)))
                .isFalse();
    }

    @Test
    void corpusWideListingPatterns_areAggregate() {
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideAggregate("dime qué actas tienen 20 asistentes"))
                .isTrue();
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideAggregate("dime los lugares donde se han realizado las actas"))
                .isTrue();
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideAggregate("en cuántas actas aparece beatriz suárez"))
                .isTrue();
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideAggregate("resume todo lo tratado sobre calefacción"))
                .isTrue();
    }

    @Test
    void febreroTemasAttendeeCompound_isCorpusWideAggregate() {
        String query =
                "¿Qué temas se discutieron en las reuniones celebradas en febrero que contaron con más de 15 asistentes?";
        assertThat(ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(query.toLowerCase()))
                .isTrue();
        assertThat(ActaFieldAnchorHeuristics.isCorpusWideAggregate(query.toLowerCase()))
                .isTrue();
    }

    @Test
    void explicitActaPdfReference_detected() {
        assertThat(ActaFieldAnchorHeuristics.hasExplicitActaDocumentReference(
                        "¿Qué acuerdo se tomó sobre el ascensor en ACTA 6.pdf?"))
                .isTrue();
        assertThat(ActaFieldAnchorHeuristics.hasExplicitActaDocumentReference("presidente del acta 2"))
                .isFalse();
    }
}
