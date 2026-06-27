package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationFollowUpResolverTest {

    @Test
    void expand_esaReunion_usesMostRecentDateFromHistory() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "El presidente en 25/02/2026 fue Jorge Moreno Navarro."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Cuántos participantes asistieron a esa reunión?"))
                .get()
                .asString()
                .contains("25/02/2026")
                .contains("la reunión del");
    }

    @Test
    void expand_esaReunion_afterPresidentQuestion_anchorsUserDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Quién fue el presidente en el acta del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "Jorge Moreno Navarro fue el presidente."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Cuántos participantes asistieron a esa reunión?"))
                .get()
                .asString()
                .isEqualTo("¿Cuántos participantes asistieron a la reunión del 25/02/2026?");
    }

    @Test
    void expand_ellos_whenParticipantsQuestion() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.ASSISTANT,
                                "En el acta del 25 de febrero de 2026 asistieron 17 participantes."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Quiénes fueron ellos?"))
                .get()
                .asString()
                .contains("25 de febrero de 2026");
    }

    @Test
    void expand_ellos_prefersUserTurnDateOverAssistantWrongDate() {
        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                1,
                                MessageRole.USER,
                                "¿Cuántos participantes asistieron a la reunión del 25/02/2026?"),
                        new ConversationMemoryTurn(
                                UUID.randomUUID(),
                                2,
                                MessageRole.ASSISTANT,
                                "En el acta del 25/02/2025 asistieron 20 participantes."));

        assertThat(ConversationFollowUpResolver.expand(history, "¿Quiénes fueron ellos?"))
                .get()
                .asString()
                .contains("25/02/2026")
                .doesNotContain("25/02/2025");
    }

    @Test
    void expand_bareParticipantCount_withoutHistory_returnsEmpty() {
        assertThat(ConversationFollowUpResolver.expand(List.of(), "¿Cuántos participantes asistieron?"))
                .isEmpty();
    }
}
