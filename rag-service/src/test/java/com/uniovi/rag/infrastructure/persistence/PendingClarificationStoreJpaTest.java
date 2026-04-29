package com.uniovi.rag.infrastructure.persistence;

import com.uniovi.rag.application.port.PendingClarificationLoad;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingClarificationStoreJpaTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private JpaPendingClarificationStore store;

    @Test
    void jsonMapper_roundTripsAllFields() {
        PendingClarificationState original =
                new PendingClarificationState(
                        PendingClarificationState.SCHEMA_VERSION,
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        "base",
                        ClarificationQuestionKind.MISSING_PERSON.templateText(),
                        ClarificationQuestionKind.MISSING_PERSON,
                        List.of("a", "b"),
                        List.of("r1"),
                        Instant.parse("2026-03-01T12:34:56Z"),
                        "correlation-x");
        Map<String, Object> map = PendingClarificationJsonMapper.toMap(original);
        PendingClarificationState parsed = PendingClarificationJsonMapper.fromMap(map);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void fromMap_rejectsWrongVersion() {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("clarificationStateVersion", 2);
        bad.put("originatingUserMessageId", UUID.randomUUID().toString());
        bad.put("baseQueryTextForClarification", "b");
        bad.put("clarificationQuestionText", ClarificationQuestionKind.MISSING_DATE.templateText());
        bad.put("requestedFields", List.of());
        bad.put("clarificationReasons", List.of());
        bad.put("createdAt", Instant.now().toString());
        bad.put("correlationId", "c");
        bad.put("questionKind", ClarificationQuestionKind.MISSING_DATE.name());
        assertThat(PendingClarificationJsonMapper.fromMap(bad)).isNull();
    }

    @Test
    void load_invalidJson_returnsInvalidLoad() {
        UUID conv = UUID.randomUUID();
        ConversationEntity entity = Mockito.mock(ConversationEntity.class);
        Map<String, Object> badJson = new LinkedHashMap<>();
        badJson.put("clarificationStateVersion", 99);
        when(entity.getPendingClarification()).thenReturn(badJson);
        when(conversationRepository.findById(conv)).thenReturn(Optional.of(entity));

        PendingClarificationLoad load = store.load(conv);
        assertThat(load.invalidJsonOrVersion()).isTrue();
        assertThat(load.state()).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveReplace_writesJsonOnEntity() {
        UUID conv = UUID.randomUUID();
        ConversationEntity entity = Mockito.mock(ConversationEntity.class);
        when(conversationRepository.findById(conv)).thenReturn(Optional.of(entity));

        PendingClarificationState state =
                new PendingClarificationState(
                        PendingClarificationState.SCHEMA_VERSION,
                        UUID.randomUUID(),
                        "b",
                        ClarificationQuestionKind.MISSING_TOPIC.templateText(),
                        ClarificationQuestionKind.MISSING_TOPIC,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "cid");

        store.saveReplace(conv, state);

        ArgumentCaptor<Map<String, Object>> jsonCap = ArgumentCaptor.forClass(Map.class);
        verify(entity).setPendingClarification(jsonCap.capture());
        verify(entity).touchUpdated();
        verify(conversationRepository).save(entity);
        assertThat(PendingClarificationJsonMapper.fromMap(jsonCap.getValue())).isEqualTo(state);
    }

    @Test
    void clear_setsPendingToNull() {
        UUID conv = UUID.randomUUID();
        ConversationEntity entity = Mockito.mock(ConversationEntity.class);
        when(conversationRepository.findById(conv)).thenReturn(Optional.of(entity));

        store.clear(conv);

        verify(entity).setPendingClarification(null);
        verify(entity).touchUpdated();
        verify(conversationRepository).save(entity);
    }
}
