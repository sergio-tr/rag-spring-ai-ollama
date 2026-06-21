package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPlanSlotEnricherTest {

    @Test
    void fillsAttendeesFieldForParticipantsQuery() {
        Map<String, String> slots =
                QueryPlanSlotEnricher.enrich(
                        "dime los participantes del acta del 25 de febrero de 2026",
                        Optional.of(QueryType.GET_FIELD),
                        Map.of());
        assertThat(slots).containsEntry("field", "attendees");
    }

    @Test
    void fillsAgendaFieldForOrdenDelDiaQuery() {
        Map<String, String> slots =
                QueryPlanSlotEnricher.enrich(
                        "¿Cuáles fueron los puntos del orden del día del 24 de febrero de 2025?",
                        Optional.of(QueryType.GET_FIELD),
                        Map.of());
        assertThat(slots).containsEntry("field", "agenda");
    }
}
