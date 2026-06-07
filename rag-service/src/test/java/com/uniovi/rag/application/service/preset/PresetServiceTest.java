package com.uniovi.rag.application.service.preset;

import com.uniovi.rag.interfaces.rest.dto.CreateRagPresetRequest;
import com.uniovi.rag.interfaces.rest.dto.UpdateRagPresetRequest;
import com.uniovi.rag.infrastructure.persistence.jpa.RagPresetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.ConfigProfileRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.service.AuditApplicationService;
import com.uniovi.rag.application.service.config.UserProjectConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresetServiceTest {

    @Mock
    private RagPresetRepository ragPresetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProjectConfigurationService userProjectConfigurationService;

    @Mock
    private AuditApplicationService auditApplicationService;

    @Mock
    private ConfigProfileRepository configProfileRepository;

    @InjectMocks
    private PresetService presetService;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void wireTransactionalSelfProxy() {
        ReflectionTestUtils.setField(presetService, "self", presetService);
    }

    @Test
    void list_mapsVisiblePresets() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "p1", null, List.of(), Map.of("topK", 3), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        when(ragPresetRepository.findVisibleForUserWithProfileRefs(userId)).thenReturn(List.of(e));

        var out = presetService.list(userId);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().name()).isEqualTo("p1");
        assertThat(out.getFirst().values()).containsEntry("topK", 3);
    }

    @Test
    void create_persistsUserOwnedPreset() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        RagPresetEntity[] lastSaved = {null};
        when(ragPresetRepository.save(any(RagPresetEntity.class)))
                .thenAnswer(
                        inv -> {
                            RagPresetEntity saved = inv.getArgument(0);
                            if (ReflectionTestUtils.getField(saved, "id") == null) {
                                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                            }
                            lastSaved[0] = saved;
                            return saved;
                        });
        when(ragPresetRepository.findByIdWithProfileRefs(any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(lastSaved[0]));

        var req = new CreateRagPresetRequest("  my preset  ", null, List.of("t"), Map.of("topK", 5), List.of());
        var dto = presetService.create(userId, req);

        assertThat(dto.name()).isEqualTo("my preset");
        assertThat(dto.tags()).containsExactly("t");
        ArgumentCaptor<RagPresetEntity> cap = ArgumentCaptor.forClass(RagPresetEntity.class);
        verify(ragPresetRepository, times(2)).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("my preset");
        assertThat(cap.getValue().isSystem()).isFalse();
    }

    @Test
    void update_systemPreset_forbidden() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "s", null, List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setSystem(true);
        when(ragPresetRepository.findByIdAndOwner_Id(e.getId(), userId)).thenReturn(Optional.of(e));

        assertThatThrownBy(
                        () ->
                                presetService.update(
                                        userId, e.getId(), new UpdateRagPresetRequest("x", null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_systemPreset_forbidden() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "s", null, List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setSystem(true);
        when(ragPresetRepository.findByIdAndOwner_Id(e.getId(), userId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> presetService.delete(userId, e.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireVisiblePreset_systemVisibleWithoutOwnerCheck() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "sys", null, List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setSystem(true);
        when(ragPresetRepository.findById(e.getId())).thenReturn(Optional.of(e));

        RagPresetEntity got = presetService.requireVisiblePreset(userId, e.getId());

        assertThat(got.getName()).isEqualTo("sys");
    }

    @Test
    void requireVisiblePreset_otherUserForbidden() {
        UUID userId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(other);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "p", null, List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        when(ragPresetRepository.findById(e.getId())).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> presetService.requireVisiblePreset(userId, e.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void applyInitialPresetToProject_copiesSanitizedValues() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "p", null, List.of(), Map.of("topK", 7), T0, T0);
        ReflectionTestUtils.setField(e, "id", presetId);
        when(ragPresetRepository.findById(presetId)).thenReturn(Optional.of(e));

        presetService.applyInitialPresetToProject(userId, projectId, presetId);

        verify(userProjectConfigurationService).putProjectConfig(eq(userId), eq(projectId), any(Map.class));
    }

    @Test
    void get_returnsDtoForVisiblePreset() {
        UUID userId = UUID.randomUUID();
        UUID presetId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        when(owner.getId()).thenReturn(userId);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "n", "d", List.of("x"), Map.of("k", 1), T0, T0);
        ReflectionTestUtils.setField(e, "id", presetId);
        when(ragPresetRepository.findById(presetId)).thenReturn(Optional.of(e));
        when(ragPresetRepository.findByIdWithProfileRefs(presetId)).thenReturn(Optional.of(e));

        var dto = presetService.get(userId, presetId);

        assertThat(dto.id()).isEqualTo(presetId);
        assertThat(dto.name()).isEqualTo("n");
        assertThat(dto.description()).isEqualTo("d");
    }

    @Test
    void update_userOwned_appliesPartialFields() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "old", "old-desc", List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        when(ragPresetRepository.findByIdAndOwner_Id(e.getId(), userId)).thenReturn(Optional.of(e));
        when(ragPresetRepository.save(any(RagPresetEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragPresetRepository.findByIdWithProfileRefs(e.getId())).thenReturn(Optional.of(e));

        presetService.update(
                userId,
                e.getId(),
                new UpdateRagPresetRequest(null, "  ", List.of("t"), Map.of("topK", 2), null));

        assertThat(e.getDescription()).isNull();
        assertThat(e.getTags()).containsExactly("t");
        assertThat(e.getValues()).containsEntry("topK", 2);
    }

    @Test
    void delete_userOwned_removesRow() {
        UUID userId = UUID.randomUUID();
        UserEntity owner = mock(UserEntity.class);
        RagPresetEntity e = RagPresetEntity.newUserOwned(owner, "z", null, List.of(), Map.of(), T0, T0);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        when(ragPresetRepository.findByIdAndOwner_Id(e.getId(), userId)).thenReturn(Optional.of(e));

        presetService.delete(userId, e.getId());

        verify(ragPresetRepository).delete(e);
    }

    @Test
    void requireVisiblePreset_missing_returns404() {
        UUID id = UUID.randomUUID();
        when(ragPresetRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> presetService.requireVisiblePreset(UUID.randomUUID(), id))
                .isInstanceOf(ResponseStatusException.class);
    }
}
