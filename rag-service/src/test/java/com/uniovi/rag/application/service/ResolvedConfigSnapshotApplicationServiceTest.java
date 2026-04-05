package com.uniovi.rag.application.service;

import com.uniovi.rag.application.config.ConfigResolverService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedConfigSnapshot;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.mapper.ResolvedConfigSnapshotEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.CreateResolvedConfigSnapshotRequest;
import com.uniovi.rag.interfaces.rest.dto.ResolvedConfigSnapshotCreatedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedConfigSnapshotApplicationServiceTest {

    @Mock
    private ConfigResolverService configResolverService;

    @Mock
    private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;

    @Mock
    private ResolvedConfigSnapshotEntityMapper resolvedConfigSnapshotEntityMapper;

    @InjectMocks
    private ResolvedConfigSnapshotApplicationService service;

    @Test
    void create_invokesResolveSnapshotMapperAndSave() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig core = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "a", "b", "c", "simple");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        core,
                        CapabilitySet.fromRagConfig(core),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "hello",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        core);
        ResolvedConfigSnapshot snap =
                new ResolvedConfigSnapshot(
                        UUID.randomUUID(),
                        Instant.now(),
                        resolved,
                        resolved.capabilitySet(),
                        resolved.compatibility(),
                        resolved.reindexImpact(),
                        resolved.effectiveSystemPrompt(),
                        resolved.provenance());
        ResolvedConfigSnapshotEntity entity = ResolvedConfigSnapshotEntity.newForInsert();
        entity.setId(UUID.randomUUID());
        entity.setConfigHash("hash");
        entity.setCreatedAt(Instant.now());

        when(configResolverService.resolve(any())).thenReturn(resolved);
        when(configResolverService.snapshot(resolved)).thenReturn(snap);
        when(resolvedConfigSnapshotEntityMapper.toNewEntity(
                        eq(resolved), eq(snap), eq(userId), any(), any(), any(), any(), any()))
                .thenReturn(entity);
        when(resolvedConfigSnapshotRepository.save(entity)).thenReturn(entity);

        CreateResolvedConfigSnapshotRequest req =
                new CreateResolvedConfigSnapshotRequest(projectId, null, null, null, null, null, null, null, null);

        ResolvedConfigSnapshotCreatedResponse out = service.createFromRequest(userId, req);

        verify(configResolverService).resolve(any());
        verify(configResolverService).snapshot(resolved);
        assertThat(out.id()).isEqualTo(entity.getId());
        assertThat(out.configHash()).isEqualTo("hash");

        ArgumentCaptor<String> hashCap = ArgumentCaptor.forClass(String.class);
        verify(resolvedConfigSnapshotEntityMapper)
                .toNewEntity(eq(resolved), eq(snap), eq(userId), hashCap.capture(), any(), any(), any(), any());
        assertThat(hashCap.getValue()).isNotBlank();
    }

    @Test
    void get_foreignUser_returns404() {
        UUID userId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        ResolvedConfigSnapshotEntity entity = ResolvedConfigSnapshotEntity.newForInsert();
        entity.setId(snapId);
        entity.setProvenanceJsonb(
                Map.of(
                        ResolvedConfigSnapshotEntityMapper.PROVENANCE_CREATING_USER_ID,
                        UUID.randomUUID().toString()));

        when(resolvedConfigSnapshotRepository.findById(snapId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getByIdForUser(userId, snapId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
