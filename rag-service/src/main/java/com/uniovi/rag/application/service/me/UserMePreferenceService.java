package com.uniovi.rag.application.service.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserPreferencesEntity;
import com.uniovi.rag.interfaces.rest.dto.me.MePreferencesResponse;
import com.uniovi.rag.interfaces.rest.dto.me.MePutPreferencesRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserMePreferenceService {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final ObjectMapper objectMapper;

    public UserMePreferenceService(
            UserRepository userRepository,
            UserPreferencesRepository userPreferencesRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userPreferencesRepository = userPreferencesRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MePreferencesResponse get(UUID userId) {
        return userPreferencesRepository
                .findById(userId)
                .map(e -> new MePreferencesResponse(e.getSchemaVersion(), copyMap(e.getPreferences())))
                .orElse(new MePreferencesResponse(CURRENT_SCHEMA_VERSION, Map.of()));
    }

    @Transactional
    public MePreferencesResponse put(UUID userId, MePutPreferencesRequest body) {
        int requested =
                body.schemaVersion() != null ? body.schemaVersion() : CURRENT_SCHEMA_VERSION;
        if (requested < CURRENT_SCHEMA_VERSION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Client schema version is obsolete; minimum supported is " + CURRENT_SCHEMA_VERSION);
        }
        UserJsonBlobValidator.validateV1Map(body.preferences(), objectMapper);

        UserEntity user = userRepository.findById(userId).orElseThrow();
        UserPreferencesEntity entity =
                userPreferencesRepository
                        .findById(userId)
                        .orElseGet(() -> UserPreferencesEntity.newForUser(user));
        entity.setPreferences(new LinkedHashMap<>(body.preferences()));
        entity.setSchemaVersion(CURRENT_SCHEMA_VERSION);
        entity.setUpdatedAt(Instant.now());
        userPreferencesRepository.save(entity);
        return new MePreferencesResponse(entity.getSchemaVersion(), copyMap(entity.getPreferences()));
    }

    private static Map<String, Object> copyMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }
}
