package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPersonalizationEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MePersonalizationResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPersonalizationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserMePersonalizationService {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final UserRepository userRepository;
    private final UserPersonalizationRepository userPersonalizationRepository;
    private final ObjectMapper objectMapper;

    public UserMePersonalizationService(
            UserRepository userRepository,
            UserPersonalizationRepository userPersonalizationRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userPersonalizationRepository = userPersonalizationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MePersonalizationResponse get(UUID userId) {
        return userPersonalizationRepository
                .findById(userId)
                .map(e -> new MePersonalizationResponse(e.getSchemaVersion(), copyMap(e.getPersonalization())))
                .orElse(new MePersonalizationResponse(CURRENT_SCHEMA_VERSION, Map.of()));
    }

    @Transactional
    public MePersonalizationResponse put(UUID userId, MePutPersonalizationRequest body) {
        int requested =
                body.schemaVersion() != null ? body.schemaVersion() : CURRENT_SCHEMA_VERSION;
        if (requested < CURRENT_SCHEMA_VERSION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Client schema version is obsolete; minimum supported is " + CURRENT_SCHEMA_VERSION);
        }
        UserJsonBlobValidator.validateV1Map(body.personalization(), objectMapper);

        UserEntity user = userRepository.findById(userId).orElseThrow();
        UserPersonalizationEntity entity =
                userPersonalizationRepository
                        .findById(userId)
                        .orElseGet(() -> UserPersonalizationEntity.newForUser(user));
        entity.setPersonalization(new LinkedHashMap<>(body.personalization()));
        entity.setSchemaVersion(CURRENT_SCHEMA_VERSION);
        entity.setUpdatedAt(Instant.now());
        userPersonalizationRepository.save(entity);
        return new MePersonalizationResponse(
                entity.getSchemaVersion(), copyMap(entity.getPersonalization()));
    }

    private static Map<String, Object> copyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }
}
