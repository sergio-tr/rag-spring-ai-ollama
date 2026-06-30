package com.uniovi.rag.application.service.config;

import com.uniovi.rag.application.config.PromptTemplateValidator;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProjectConfigurationServiceTest {

    @Mock
    private ConfigResolver configResolver;

    @Mock
    private ObjectProvider<ConfigResolver> configResolverProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RagConfigurationRepository ragConfigurationRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private PromptTemplateValidator promptTemplateValidator;

    @InjectMocks
    private UserProjectConfigurationService service;

    @Test
    void getEffectiveUserConfig_delegatesToResolver() {
        UUID uid = UUID.randomUUID();
        when(configResolverProvider.getObject()).thenReturn(configResolver);
        RagConfig cfg = RagConfig.fromFeatureConfiguration(
                new RagFeatureConfiguration(),
                3,
                0.5,
                "m",
                "e",
                "c",
                "SIMPLE");
        when(configResolver.resolve(uid, null, null)).thenReturn(cfg);

        Map<String, Object> m = service.getEffectiveUserConfig(uid);

        assertThat(m.get("topK")).isEqualTo(3);
    }

    @Test
    void putUserConfig_updatesExistingRow() {
        UUID uid = UUID.randomUUID();
        when(configResolverProvider.getObject()).thenReturn(configResolver);
        var user = mock(UserEntity.class);
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        RagConfigurationEntity row = mock(RagConfigurationEntity.class);
        when(ragConfigurationRepository.findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(
                        uid, RagConfigurationLevel.USER_DEFAULT))
                .thenReturn(Optional.of(row));

        when(configResolver.resolve(uid, null, null))
                .thenReturn(
                        RagConfig.fromFeatureConfiguration(
                                new RagFeatureConfiguration(),
                                5,
                                0.7,
                                "a",
                                "b",
                                "c",
                                "SIMPLE"));

        Map<String, Object> out = service.putUserConfig(uid, Map.of("topK", 12));

        assertThat(out.get("topK")).isEqualTo(5);
        verify(ragConfigurationRepository).save(any(RagConfigurationEntity.class));
    }

    @Test
    void deleteProjectConfig_deletesWhenPresent() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(projectAccessService.requireOwnedProject(uid, pid)).thenReturn(mock(ProjectEntity.class));
        RagConfigurationEntity row = mock(RagConfigurationEntity.class);
        when(ragConfigurationRepository.findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        uid, pid, RagConfigurationLevel.PROJECT))
                .thenReturn(Optional.of(row));

        service.deleteProjectConfig(uid, pid);

        verify(ragConfigurationRepository).delete(row);
    }

    @Test
    void mergeProjectConfig_preservesExistingKeysAndAppliesPatch() {
        UUID uid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        when(configResolverProvider.getObject()).thenReturn(configResolver);
        ProjectEntity project = mock(ProjectEntity.class);
        when(projectAccessService.requireOwnedProject(uid, pid)).thenReturn(project);
        var user = mock(UserEntity.class);
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));

        RagConfigurationEntity row = mock(RagConfigurationEntity.class);
        Map<String, Object> existingVals = new LinkedHashMap<>();
        existingVals.put("topK", 5);
        when(row.getValues()).thenReturn(existingVals);
        when(ragConfigurationRepository.findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        uid, pid, RagConfigurationLevel.PROJECT))
                .thenReturn(Optional.of(row));

        when(configResolver.resolve(uid, pid, null))
                .thenReturn(
                        RagConfig.fromFeatureConfiguration(
                                new RagFeatureConfiguration(),
                                5,
                                0.7,
                                "a",
                                "b",
                                "c",
                                "SIMPLE"));

        service.mergeProjectConfig(uid, pid, Map.of("classifierModelId", "my-tag"));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(row).setValues(cap.capture());
        assertThat(cap.getValue()).containsEntry("topK", 5).containsEntry("classifierModelId", "my-tag");
        verify(ragConfigurationRepository).save(row);
    }
}
