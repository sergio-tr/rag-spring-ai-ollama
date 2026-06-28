package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import org.junit.jupiter.api.Test;

class ClassifierDeterministicResolverTest {

    @Test
    void resolvesMatrixQuestionsWithoutClassifier() {
        assertThat(ClassifierDeterministicResolver.resolve("¿Cuántos participantes asistieron a la reunión del 25/02/2026?"))
                .contains(QueryType.GET_FIELD);
        assertThat(ClassifierDeterministicResolver.resolve("¿Qué actas tienen hora de inicio a las 19:00?"))
                .contains(QueryType.FILTER_AND_LIST);
        assertThat(ClassifierDeterministicResolver.resolve("¿En qué actas aparece Juan Pérez?"))
                .contains(QueryType.FIND_PARAGRAPH);
        assertThat(ClassifierDeterministicResolver.resolve("¿Hay actas con menos de 10 participantes?"))
                .contains(QueryType.BOOLEAN_QUERY);
        assertThat(ClassifierDeterministicResolver.resolve("Resume brevemente el acta del 25/02/2026."))
                .contains(QueryType.SUMMARIZE_MEETING);
        assertThat(
                        ClassifierDeterministicResolver.resolve(
                                "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?"))
                .contains(QueryType.FILTER_AND_LIST);
    }

    @Test
    void ambiguousPresidentWithoutDateIsNotDeterministic() {
        assertThat(ClassifierDeterministicResolver.resolve("¿Quién fue el presidente?")).isEmpty();
    }
}
