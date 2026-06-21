package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassifierOverridesTest {

    @Test
    void presencePhrasesMapToBooleanQuery() {
        assertEquals(QueryType.BOOLEAN_QUERY, ClassifierOverrides.apply("confirma si aparece X", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishParticipantsWithDateMapsToGetField() {
        assertEquals(
                QueryType.GET_FIELD,
                ClassifierOverrides.apply(
                        "dime los participantes del acta del 25 de febrero de 2026",
                        QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void spanishCountActasMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply("¿Cuántas actas mencionan el ascensor?", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishDurationWithDateMapsToGetDuration() {
        assertEquals(
                QueryType.GET_DURATION,
                ClassifierOverrides.apply(
                        "Duración de la reunión del 25 de febrero de 2026.", QueryType.GET_FIELD));
    }

    @Test
    void spanishSummaryWithDateMapsToSummarizeMeeting() {
        assertEquals(
                QueryType.SUMMARIZE_MEETING,
                ClassifierOverrides.apply("Resume la reunión del 24 de febrero de 2025.", QueryType.FIND_PARAGRAPH));
    }

    @Test
    void verifySiMapsToBooleanQuery() {
        assertEquals(
                QueryType.BOOLEAN_QUERY,
                ClassifierOverrides.apply(
                        "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                        QueryType.FIND_PARAGRAPH));
    }

    @Test
    void spanishPresidentWithDateMapsToGetFieldViaMatchRule() {
        assertEquals(
                QueryType.GET_FIELD,
                ClassifierOverrides.matchRule("¿Quién presidió el acta del 25 de febrero de 2026?").orElseThrow());
    }

    @Test
    void spanishCountMeetingsInYearMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply("¿Cuántas reuniones se realizaron en 2025?", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishPersonActaCountMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply("¿En cuántas actas aparece Juan Pérez Gutiérrez?", QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void spanishBudgetCountMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply("¿En cuántas reuniones se habló sobre presupuestos?", QueryType.FIND_PARAGRAPH));
    }

    @Test
    void spanishAttendeeCountWithDateMapsToGetField() {
        assertEquals(
                QueryType.GET_FIELD,
                ClassifierOverrides.apply(
                        "¿Cuántas personas asistieron a la reunión del 25 de febrero de 2025?",
                        QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void spanishLessThanTenParticipantsMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply(
                        "¿Cuántas actas tuvieron menos de diez participantes?", QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void noOverrideReturnsOriginal() {
        assertEquals(QueryType.COUNT_DOCUMENTS, ClassifierOverrides.apply("cuántos documentos hay", QueryType.COUNT_DOCUMENTS));
    }
}
