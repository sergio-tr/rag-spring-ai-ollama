package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPlanSlotEnricherTest {

    @Test
    void fillsStartTimeFieldForStartEndActaQuery() {
        assertThat(
                        QueryPlanSlotEnricher.inferFieldSlot(
                                "a qué hora empezó y a qué hora terminó esa acta? en el acta del 24 de febrero de 2025"))
                .contains("startEndTime");
    }

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

    @Test
    void fillsRoleFieldForPapelTuvoPartialNameQuery() {
        Map<String, String> slots =
                QueryPlanSlotEnricher.enrich(
                        "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                        Optional.of(QueryType.GET_FIELD),
                        Map.of());
        assertThat(slots).containsEntry("field", "role");
    }

    @Test
    void countQueryOverridesPreclassifiedAttendeesSlot() {
        Map<String, String> slots =
                QueryPlanSlotEnricher.enrich(
                        "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                        Optional.of(QueryType.GET_FIELD),
                        Map.of("field", "attendees"));
        assertThat(slots).containsEntry("field", "attendeesCount");
    }
}
