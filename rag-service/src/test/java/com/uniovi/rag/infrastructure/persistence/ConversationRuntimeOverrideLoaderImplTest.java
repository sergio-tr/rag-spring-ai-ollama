package com.uniovi.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationRuntimeOverrideLoaderImplTest {

    private static final String USE_RETRIEVAL = "useRetrieval";

    @Mock
    private ConversationRepository conversationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadRuntimeOverrideReturnsJsonOnlyForConversationOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = conversation(ownerId, Map.of(USE_RETRIEVAL, true, "rankerEnabled", false));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        Optional<JsonNode> loaded =
                new ConversationRuntimeOverrideLoaderImpl(conversationRepository, objectMapper)
                        .loadRuntimeOverride(ownerId, conversationId);

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().get(USE_RETRIEVAL).asBoolean()).isTrue();
        assertThat(loaded.orElseThrow().get("rankerEnabled").asBoolean()).isFalse();
    }

    @Test
    void loadRuntimeOverrideRejectsForeignConversationAndMissingIds() {
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ConversationEntity conversation = conversation(UUID.randomUUID(), Map.of(USE_RETRIEVAL, true));
        ConversationRuntimeOverrideLoaderImpl loader =
                new ConversationRuntimeOverrideLoaderImpl(conversationRepository, objectMapper);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        assertThat(loader.loadRuntimeOverride(ownerId, conversationId)).isEmpty();
        assertThat(loader.loadRuntimeOverride(null, conversationId)).isEmpty();
        assertThat(loader.loadRuntimeOverride(ownerId, null)).isEmpty();
    }

    @Test
    void loadRuntimeOverrideIgnoresEmptyRuntimeOverride() {
        UUID ownerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findById(conversationId))
                .thenReturn(Optional.of(conversation(ownerId, Map.of())));

        Optional<JsonNode> loaded =
                new ConversationRuntimeOverrideLoaderImpl(conversationRepository, objectMapper)
                        .loadRuntimeOverride(ownerId, conversationId);

        assertThat(loaded).isEmpty();
    }

    private static ConversationEntity conversation(UUID userId, Map<String, Object> runtimeOverride) {
        UserEntity user = newUserEntity();
        user.setId(userId);
        user.setEmail(userId + "@test.local");
        user.setPasswordHash("hash");
        user.setName("Owner");
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.EPOCH);

        ConversationEntity conversation = ConversationEntity.create(user, null, "Chat", null);
        conversation.setRuntimeOverride(new LinkedHashMap<>(runtimeOverride));
        return conversation;
    }

    private static UserEntity newUserEntity() {
        try {
            var ctor = UserEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Could not create UserEntity for ownership test", ex);
        }
    }
}
