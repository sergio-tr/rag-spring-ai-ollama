package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPreferencesEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MePreferencesResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPreferencesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMePreferenceServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserMePreferenceService service;

    @BeforeEach
    void init() {
        service = new UserMePreferenceService(userRepository, userPreferencesRepository, objectMapper);
    }

    @Test
    void get_empty_returnsDefaults() {
        UUID userId = UUID.randomUUID();
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());

        MePreferencesResponse r = service.get(userId);
        assertEquals(UserMePreferenceService.CURRENT_SCHEMA_VERSION, r.schemaVersion());
        assertEquals(Map.of(), r.preferences());
    }

    @Test
    void put_createsAndSaves() {
        UUID userId = UUID.randomUUID();
        UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPreferencesRepository.save(any(UserPreferencesEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MePutPreferencesRequest req = new MePutPreferencesRequest(1, Map.of("theme", "dark"));
        MePreferencesResponse r = service.put(userId, req);
        assertEquals(UserMePreferenceService.CURRENT_SCHEMA_VERSION, r.schemaVersion());
        verify(userPreferencesRepository).save(any(UserPreferencesEntity.class));
    }
}
