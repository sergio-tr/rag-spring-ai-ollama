package com.uniovi.rag.application.service.knowledge.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ActaSectionChunkerTest {

    @Test
    void chunk_acta1_producesSectionAwareChunksWithBoundaries() throws IOException {
        String content = loadFixture("acta-1.txt");
        List<ActaSectionChunk> chunks = ActaSectionChunker.chunk(content, 400);

        assertThat(chunks).isNotEmpty();
        Set<String> sectionTypes = chunks.stream().map(ActaSectionChunk::sectionType).collect(Collectors.toSet());
        assertThat(sectionTypes)
                .contains(
                        ActaSectionChunk.SECTION_HEADER,
                        ActaSectionChunk.SECTION_PARTICIPANTS,
                        ActaSectionChunk.SECTION_AGENDA,
                        ActaSectionChunk.SECTION_CLOSING);

        String participants =
                chunks.stream()
                        .filter(c -> ActaSectionChunk.SECTION_PARTICIPANTS.equals(c.sectionType()))
                        .map(ActaSectionChunk::text)
                        .collect(Collectors.joining("\n"));
        assertThat(participants).contains("Juan Pérez Gutiérrez").contains("Rosa Aguilar Fernández");

        String agenda =
                chunks.stream()
                        .filter(c -> ActaSectionChunk.SECTION_AGENDA.equals(c.sectionType()))
                        .map(ActaSectionChunk::text)
                        .collect(Collectors.joining("\n"));
        assertThat(agenda).contains("Orden del día").contains("presupuesto");
    }

    @Test
    void chunk_participantsSectionIsNotSplitAcrossUnrelatedSections() throws IOException {
        String content = loadFixture("acta-1.txt");
        List<ActaSectionChunk> chunks = ActaSectionChunker.chunk(content, 400);

        for (ActaSectionChunk chunk : chunks) {
            if (ActaSectionChunk.SECTION_PARTICIPANTS.equals(chunk.sectionType())) {
                assertThat(chunk.text()).doesNotContain("Orden del día");
            }
            if (ActaSectionChunk.SECTION_AGENDA.equals(chunk.sectionType())) {
                assertThat(chunk.text()).doesNotContain("(Presidente)");
            }
        }
    }

    @Test
    void chunk_nonActa_fallsBackToPlainSplitting() {
        String content = "Paragraph one.\n\nParagraph two with enough text to require splitting when max is small.";
        List<ActaSectionChunk> chunks = ActaSectionChunker.chunk(content, 30);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> ActaSectionChunk.SECTION_BODY.equals(c.sectionType()));
    }

    @Test
    void chunk_allFiveActaFixtures_haveHeaderAndAgendaSections() throws IOException {
        for (String fixture : List.of("acta-1.txt", "acta-2.txt", "acta-3.txt", "acta-5.txt", "acta-6.txt")) {
            List<ActaSectionChunk> chunks = ActaSectionChunker.chunk(loadFixture(fixture), 400);
            Set<String> types = chunks.stream().map(ActaSectionChunk::sectionType).collect(Collectors.toSet());
            assertThat(types)
                    .as(fixture)
                    .contains(ActaSectionChunk.SECTION_HEADER, ActaSectionChunk.SECTION_AGENDA);
        }
    }

    private static String loadFixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/acta-fixtures", name), StandardCharsets.UTF_8);
    }
}
