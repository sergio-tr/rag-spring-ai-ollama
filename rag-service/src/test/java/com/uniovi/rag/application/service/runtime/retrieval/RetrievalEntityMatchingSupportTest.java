package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalEntityMatchingSupportTest {

    @Test
    void matchesTopicSynonymForElectricProblems() {
        assertThat(
                        RetrievalEntityMatchingSupport.containsEntityToken(
                                "se debatieron problemas en la instalacion electrica", "electrico"))
                .isTrue();
    }

    @Test
    void metadataDateIsoMatchesSlashDateToken() {
        Map<String, Object> meta = Map.of("date_iso", "2026-02-25");
        assertThat(RetrievalEntityMatchingSupport.metadataContainsDateToken(meta, "2026-02-25")).isTrue();
    }

    @Test
    void metadataPersonMatchesPresidentField() {
        Map<String, Object> meta = Map.of("president", "Jorge Moreno Navarro");
        assertThat(RetrievalEntityMatchingSupport.metadataContainsPerson(meta, "Jorge Moreno")).isTrue();
    }

    @Test
    void metadataAttendeesListMatchesParticipant() {
        Map<String, Object> meta = Map.of("attendees", List.of("Ana Sánchez Herrera", "Pedro Jiménez"));
        assertThat(RetrievalEntityMatchingSupport.metadataContainsPerson(meta, "Ana Sanchez")).isTrue();
    }
}
