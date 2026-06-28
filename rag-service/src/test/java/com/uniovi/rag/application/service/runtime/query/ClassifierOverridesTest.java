package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void spanishParticipantCountWithDateMapsToGetField() {
        assertEquals(
                QueryType.GET_FIELD,
                ClassifierOverrides.apply(
                        "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                        QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void spanishLessThanTenParticipantsMapsToCountDocuments() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply(
                        "¿Cuántas actas tuvieron menos de diez participantes?", QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void spanishCommonSectionsMapsToExtractEntities() {
        assertEquals(
                QueryType.EXTRACT_ENTITIES,
                ClassifierOverrides.apply(
                        "¿Qué secciones comunes aparecen en todas las actas?", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishActasMentionCamerasMapsToFilterAndList() {
        assertEquals(
                QueryType.FILTER_AND_LIST,
                ClassifierOverrides.apply(
                        "¿Qué actas mencionan cámaras de seguridad?", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishActasStartTimeMapsToFilterAndList() {
        assertEquals(
                QueryType.FILTER_AND_LIST,
                ClassifierOverrides.apply(
                        "¿Qué actas tienen hora de inicio a las 19:00?", QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishPersonRolePartialNameMapsToGetField() {
        assertEquals(
                QueryType.GET_FIELD,
                ClassifierOverrides.apply(
                        "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                        QueryType.EXTRACT_ENTITIES));
    }

    @Test
    void spanishHayActasWithThresholdMapsToBoolean() {
        assertEquals(
                QueryType.BOOLEAN_QUERY,
                ClassifierOverrides.apply(
                        "¿Hay actas con menos de 10 participantes?", QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void spanishAugustVideovigilanciaCompoundFilterMapsToFilterAndList() {
        assertEquals(
                QueryType.FILTER_AND_LIST,
                ClassifierOverrides.apply(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.SUMMARIZE_MEETING));
    }

    @Test
    void spanishInQueActasAppearsMapsToFindParagraph() {
        assertEquals(
                QueryType.FIND_PARAGRAPH,
                ClassifierOverrides.apply("¿En qué actas aparece Juan Pérez?", QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void undatedParticipantCount_doesNotMatchCountDocumentsOverride() {
        assertEquals(
                QueryType.COUNT_DOCUMENTS,
                ClassifierOverrides.apply("¿Cuántos participantes asistieron?", QueryType.COUNT_DOCUMENTS));
        assertTrue(
                ClassifierOverrides.shouldRejectCountDocumentsForUndatedParticipantCount(
                        "¿Cuántos participantes asistieron?", QueryType.COUNT_DOCUMENTS));
    }

    @Test
    void fdSm02_yearOnlyResumeMapsToSummarizeMeeting() {
        assertEquals(
                QueryType.SUMMARIZE_MEETING,
                ClassifierOverrides.apply("Resume el acta del año 2030.", QueryType.FIND_PARAGRAPH));
        assertEquals(
                QueryType.SUMMARIZE_MEETING,
                ClassifierOverrides.matchRule("Resume el acta del año 2030.").orElseThrow());
    }

    @Test
    void fdFp01_whatWasSaidAboutTopicMapsToFindParagraph() {
        assertEquals(
                QueryType.FIND_PARAGRAPH,
                ClassifierOverrides.apply(
                        "¿Qué se dijo en relación a la limpieza de las zonas comunes?",
                        QueryType.COUNT_AND_EXPLAIN));
    }

    @Test
    void fdFp02_whatWasCommentedMapsToFindParagraph() {
        assertEquals(
                QueryType.FIND_PARAGRAPH,
                ClassifierOverrides.apply(
                        "¿Qué se comentó respecto a la fuga de gas?", QueryType.COUNT_AND_EXPLAIN));
    }

    @Test
    void fdCe02_exactAttendeeListingWithDecisionMapsToCountAndExplain() {
        assertEquals(
                QueryType.COUNT_AND_EXPLAIN,
                ClassifierOverrides.apply(
                        "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?",
                        QueryType.FIND_PARAGRAPH));
    }

    @Test
    void verifySiStillMapsToBooleanDespiteMencionCue() {
        assertEquals(
                QueryType.BOOLEAN_QUERY,
                ClassifierOverrides.apply(
                        "Verifica si se mencionó la limpieza en alguna reunión celebrada en 2026.",
                        QueryType.COUNT_DOCUMENTS));
    }
}
