package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MePersonalizationResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPersonalizationRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMePersonalizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPersonalizationRepository userPersonalizationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserMePersonalizationService service;

    @BeforeEach
    void init() {
        service = new UserMePersonalizationService(userRepository, userPersonalizationRepository, objectMapper);
    }

    @Test
    void get_empty_returnsDefaults() {
        UUID userId = UUID.randomUUID();
        when(userPersonalizationRepository.findById(userId)).thenReturn(Optional.empty());

        MePersonalizationResponse r = service.get(userId);
        assertEquals(UserMePersonalizationService.CURRENT_SCHEMA_VERSION, r.schemaVersion());
        assertEquals(Map.of(), r.personalization());
    }

    @Test
    void put_createsAndSaves() {
        UUID userId = UUID.randomUUID();
        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPersonalizationRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPersonalizationRepository.save(any(UserPersonalizationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MePutPersonalizationRequest req = new MePutPersonalizationRequest(1, Map.of("k", "v"));
        MePersonalizationResponse r = service.put(userId, req);
        assertEquals(UserMePersonalizationService.CURRENT_SCHEMA_VERSION, r.schemaVersion());
        verify(userPersonalizationRepository).save(any(UserPersonalizationEntity.class));
    }
}
