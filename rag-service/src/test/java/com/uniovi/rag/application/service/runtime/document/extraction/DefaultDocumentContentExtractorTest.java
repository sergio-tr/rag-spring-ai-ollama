package com.uniovi.rag.application.service.runtime.document.extraction;

import com.uniovi.rag.domain.model.Cluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class DefaultDocumentContentExtractorTest {

    private DefaultDocumentContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DefaultDocumentContentExtractor();
    }

    @Test
    void extractDate_findsSpanishDate() {
        String content = "Fecha: 24 de febrero de 2025\nOtro texto";
        assertEquals("24 de febrero de 2025", extractor.extractDate(content));
    }

    @Test
    void extractDate_notFound_returnsUnknown() {
        assertEquals("Unknown date", extractor.extractDate("No date here"));
    }

    @Test
    void extractTime_start() {
        String content = "Hora de inicio: 10:30";
        assertEquals("10:30", extractor.extractTime(content, "start"));
    }

    @Test
    void extractTime_start_spacedHourFormatFromActaHeaders() {
        String content = "Hora de inicio: 19: 00 h\nHora de finalización: 20: 30 h";
        assertEquals("19:00", extractor.extractTime(content, "start"));
        assertEquals("20:30", extractor.extractTime(content, "end"));
    }

    @Test
    void extractTime_end() {
        String content = "Hora de finalización: 11:45";
        assertEquals("11:45", extractor.extractTime(content, "end"));
    }

    @Test
    void extractTime_notFound_returnsNull() {
        assertNull(extractor.extractTime("No time", "start"));
    }

    @Test
    void extractAttendeeCount() {
        assertEquals(5, extractor.extractAttendeeCount("Hay 5 propietarios presentes"));
        assertEquals(0, extractor.extractAttendeeCount("Sin número"));
    }

    @Test
    void extractRelevantFragment_nullOrBlankQuery_returnsEmpty() {
        assertEquals("", extractor.extractRelevantFragment("content", null));
        assertEquals("", extractor.extractRelevantFragment("content", "   "));
    }

    @Test
    void containsAnyKeyword() {
        assertTrue(extractor.containsAnyKeyword("hello world", new String[]{"world"}));
        assertFalse(extractor.containsAnyKeyword("hello", new String[]{"world"}));
        assertFalse(extractor.containsAnyKeyword(null, new String[]{"a"}));
        assertFalse(extractor.containsAnyKeyword("text", null));
    }

    @Test
    void extractLiteralField_place() {
        String content = "Lugar: Sala A";
        assertEquals("Sala A", extractor.extractLiteralField("place", content));
    }

    @Test
    void extractLiteralField_unknown_returnsNull() {
        assertNull(extractor.extractLiteralField("unknown", "content"));
    }

    @Test
    void countProposals() {
        String content = "Se plantea una propuesta. Se acuerda otra.";
        assertTrue(extractor.countProposals(content) >= 2);
    }

    @Test
    void clusterItems_singleItem() {
        List<String> items = List.of("a");
        Function<String, String> content = s -> s;
        Function<String, String> type = s -> "t";
        List<Cluster<String>> clusters = extractor.clusterItems(items, content, type, 0.5);
        assertEquals(1, clusters.size());
        assertEquals("a", clusters.get(0).getRepresentativeItem());
    }

    @Test
    void extractAttendees_parsesBulletLinesAndStripsRoles() {
        String content = """
                Asistentes:
                • Alice Example (Presidente)
                • Bob Example (Secretario)
                • Carol Example
                Orden del día:
                • Ruegos y preguntas
                """;

        List<String> attendees = extractor.extractAttendees(content);

        assertEquals(List.of("Alice Example", "Bob Example", "Carol Example"), attendees);
        assertEquals("Alice Example", extractor.extractLiteralField("president", content));
        assertEquals("Bob Example", extractor.extractLiteralField("secretary", content));
    }

    @Test
    void extractAttendees_acta5CombinedRetrieverStyle_returnsSeventeen() throws Exception {
        String base = Files.readString(Path.of("src/test/resources/acta-fixtures/acta-5.txt"));
        String content = base + "\n" + base;
        List<String> attendees = extractor.extractAttendees(content);
        assertEquals(17, attendees.size(), () -> "got " + attendees.size() + ": " + attendees);
    }

    @Test
    void extractAttendees_acta5FullDocument_returnsSeventeen() throws Exception {
        String content = Files.readString(Path.of("src/test/resources/acta-fixtures/acta-5.txt"));
        List<String> attendees = extractor.extractAttendees(content);
        assertEquals(17, attendees.size(), () -> "got " + attendees.size() + ": " + attendees);
        assertEquals("Jorge Moreno Navarro", extractor.extractLiteralField("president", content));
    }

    @Test
    void extractAttendees_roleOnFollowingLine_assignsPresident() {
        String content = """
                Asistentes: Se cuenta con la asistencia de 17 propietarios.
                • Jorge Moreno Navarro
                (Presidente)
                • Laura Díaz Castro
                • Natalia Vázquez Gutiérrez (Secretaria)
                Orden del día:
                • Ruegos y preguntas
                """;

        assertEquals("Jorge Moreno Navarro", extractor.extractLiteralField("president", content));
        assertEquals("Natalia Vázquez Gutiérrez", extractor.extractLiteralField("secretary", content));
        assertEquals(3, extractor.extractAttendees(content).size());
    }

    @Test
    void extractAgenda_findsBulletPointsAfterHeader() {
        String content = """
                Orden del día:
                • Lectura y aprobación del acta anterior
                • Estado de cuentas y presupuesto anual
                • Reparaciones y mantenimiento del edificio
                Ruegos y preguntas
                """;

        String agenda = extractor.extractAgenda(content);
        assertNotNull(agenda);
        assertTrue(agenda.toLowerCase().contains("lectura"));
        assertTrue(agenda.toLowerCase().contains("presupuesto"));
    }
}
