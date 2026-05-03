package com.uniovi.rag.application.service;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.domain.config.runtime.ConfigProfileType;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConfigProfileEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.dto.ConfigProfileResponseDto;
import com.uniovi.rag.interfaces.rest.dto.CreateConfigProfileRequest;
import com.uniovi.rag.interfaces.rest.dto.PatchConfigProfileRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigProfileApplicationServiceTest {

    @Mock
    private ConfigProfileRepository configProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ModelCatalogPort modelCatalogPort;

    @Mock
    private AuditApplicationService auditApplicationService;

    @InjectMocks
    private ConfigProfileApplicationService service;

    @Test
    void list_mapsVisibleProfiles() {
        UUID uid = UUID.randomUUID();
        ConfigProfileEntity e = mockEntityForDto(ConfigProfileType.METADATA, uid);
        when(configProfileRepository.findVisibleForUser(uid)).thenReturn(List.of(e));

        List<ConfigProfileResponseDto> out = service.list(uid);
        assertEquals(1, out.size());
        assertEquals(ConfigProfileType.METADATA.name(), out.getFirst().profileType());
    }

    @Test
    void get_notFound() {
        UUID pid = UUID.randomUUID();
        when(configProfileRepository.findById(pid)).thenReturn(Optional.empty());
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.get(UUID.randomUUID(), pid));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void get_forbiddenWhenOtherOwner() {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserEntity owner = Mockito.mock(UserEntity.class);
        when(owner.getId()).thenReturn(otherId);
        ConfigProfileEntity e = Mockito.mock(ConfigProfileEntity.class);
        when(e.getOwner()).thenReturn(owner);
        when(configProfileRepository.findById(profileId)).thenReturn(Optional.of(e));

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.get(userId, profileId));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void create_systemScope_requiresAdmin() {
        CreateConfigProfileRequest req =
                new CreateConfigProfileRequest("METADATA", 1, "l", Map.of(), true);
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.create(UUID.randomUUID(), "USER", req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void create_unknownProfileType_badRequest() {
        CreateConfigProfileRequest req =
                new CreateConfigProfileRequest("NOT_A_TYPE", 1, "l", Map.of(), false);
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.create(UUID.randomUUID(), "USER", req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_success() {
        UUID userId = UUID.randomUUID();
        UserEntity actor = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(actor));
        when(modelCatalogPort.allowedLlmNamesInGovernance()).thenReturn(Set.of());

        CreateConfigProfileRequest req =
                new CreateConfigProfileRequest("INDEX", 1, "draft", Map.of("k", 1), false);
        ConfigProfileEntity saved = mockEntityForDto(ConfigProfileType.INDEX, userId);
        when(configProfileRepository.save(any(ConfigProfileEntity.class))).thenReturn(saved);

        ConfigProfileResponseDto out = service.create(userId, "USER", req);
        assertEquals("INDEX", out.profileType());
        verify(auditApplicationService)
                .persistAuditEntry(eq(userId), eq("CONFIG_PROFILE_CREATE"), eq("config_profile"), any(), any());
    }

    @Test
    void create_rejectsModelNotInAllowlist() {
        UUID userId = UUID.randomUUID();
        when(modelCatalogPort.allowedLlmNamesInGovernance()).thenReturn(Set.of("allowed-only"));

        CreateConfigProfileRequest req =
                new CreateConfigProfileRequest(
                        "CHUNKING",
                        1,
                        "x",
                        Map.of("llmModel", "bad-model"),
                        false);

        assertThrows(ResponseStatusException.class, () -> service.create(userId, "USER", req));
    }

    @Test
    void patch_noFields_badRequest() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.patch(UUID.randomUUID(), "USER", UUID.randomUUID(), null));
    }

    @Test
    void patch_immutable_conflict() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ConfigProfileEntity e = Mockito.mock(ConfigProfileEntity.class);
        when(e.isImmutable()).thenReturn(true);
        when(configProfileRepository.findById(profileId)).thenReturn(Optional.of(e));

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.patch(
                                        userId, "USER", profileId, new PatchConfigProfileRequest("x", null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    private static ConfigProfileEntity mockEntityForDto(ConfigProfileType type, UUID ownerId) {
        UUID id = UUID.randomUUID();
        UserEntity owner = Mockito.mock(UserEntity.class);
        when(owner.getId()).thenReturn(ownerId);
        ConfigProfileEntity e = Mockito.mock(ConfigProfileEntity.class);
        when(e.getId()).thenReturn(id);
        when(e.getProfileType()).thenReturn(type);
        when(e.getVersion()).thenReturn(1);
        when(e.getLabel()).thenReturn("lab");
        when(e.getPayload()).thenReturn(Map.of());
        when(e.getOwner()).thenReturn(owner);
        when(e.isImmutable()).thenReturn(false);
        when(e.getCreatedAt()).thenReturn(Instant.parse("2025-01-01T00:00:00Z"));
        return e;
    }
}
