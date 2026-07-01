package com.uniovi.rag.application.service.runtime.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeterministicQueryRewriteShortcutsTest {

    @Test
    void findActaByTopic_rewritesWithTopic() {
        Optional<StructuredRewriteResult> out =
                DeterministicQueryRewriteShortcuts.tryRewrite(
                        "en qué acta se habló sobre la regulación del uso de las terrazas?");
        assertThat(out).isPresent();
        assertThat(out.get().rewrittenQueryText().toLowerCase()).contains("terrazas");
        assertThat(out.get().rewriteNotes()).anyMatch(n -> n.contains("FIND_ACTA_BY_TOPIC"));
    }

    @Test
    void listActasByAttribute_preservesCount() {
        Optional<StructuredRewriteResult> out =
                DeterministicQueryRewriteShortcuts.tryRewrite("dime qué actas tienen 20 asistentes");
        assertThat(out).isPresent();
        assertThat(out.get().rewrittenQueryText()).contains("20");
        assertThat(out.get().slotFilling()).containsEntry("attendeeCount", "20");
    }

    @Test
    void terrazasExpandsZonasComunes() {
        Optional<StructuredRewriteResult> out =
                DeterministicQueryRewriteShortcuts.tryRewrite(
                        "En qué acta se habló sobre la regulación del uso de las terrazas?");
        assertThat(out).isPresent();
        assertThat(out.get().rewrittenQueryText().toLowerCase()).contains("terrazas", "zonas comunes");
    }

    @Test
    void videovigilanciaCountExpandsSynonyms() {
        Optional<StructuredRewriteResult> out =
                DeterministicQueryRewriteShortcuts.tryRewrite(
                        "Dime en cuántas reuniones se trató el asunto de videovigilancia (cámaras de seguridad).");
        assertThat(out).isPresent();
        assertThat(out.get().rewrittenQueryText().toLowerCase()).contains("videovigilancia");
    }
}
