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
}
