package com.uniovi.rag.infrastructure.config;

import com.uniovi.rag.domain.RagConfigurationLevel;
import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.RagConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPersonalizationRepository;
import com.uniovi.rag.infrastructure.persistence.UserPreferencesRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.RagConfigurationEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaConfigurationSourceAdapterTest {

    @Mock
    private DefaultSystemConfigurationRepository defaultSystemRepository;

    @Mock
    private RagConfigurationRepository ragConfigurationRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private UserPersonalizationRepository userPersonalizationRepository;

    @InjectMocks
    private JpaConfigurationSourceAdapter adapter;

    @Test
    void loadSystemDefaults_emptyWhenOptionalEmpty() {
        when(defaultSystemRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThat(adapter.loadSystemDefaults()).isEmpty();
    }

    @Test
    void loadSystemDefaults_mapsNullValuesToEmptyMap() {
        DefaultSystemConfigurationEntity row = mock(DefaultSystemConfigurationEntity.class);
        when(row.getValues()).thenReturn(null);
        when(defaultSystemRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(row));

        assertThat(adapter.loadSystemDefaults()).hasValue(Map.of());
    }

    @Test
    void loadSystemDefaults_returnsValuesWhenPresent() {
        Map<String, Object> values = Map.of("k", "v");
        DefaultSystemConfigurationEntity row = mock(DefaultSystemConfigurationEntity.class);
        when(row.getValues()).thenReturn(values);
        when(defaultSystemRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(row));

        assertThat(adapter.loadSystemDefaults()).hasValue(values);
    }

    @Test
    void loadUserDefault_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        RagConfigurationEntity cfg = mock(RagConfigurationEntity.class);
        Map<String, Object> values = Map.of("a", 1);
        when(cfg.getValues()).thenReturn(values);
        when(ragConfigurationRepository.findFirstByUser_IdAndLevelAndProjectIsNullAndActiveIsTrue(
                        userId, RagConfigurationLevel.USER_DEFAULT))
                .thenReturn(Optional.of(cfg));
        when(userPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
        when(userPersonalizationRepository.findById(userId)).thenReturn(Optional.empty());

        assertThat(adapter.loadUserDefault(userId)).hasValue(values);
    }

    @Test
    void loadProject_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RagConfigurationEntity cfg = mock(RagConfigurationEntity.class);
        Map<String, Object> values = Map.of("b", 2);
        when(cfg.getValues()).thenReturn(values);
        when(ragConfigurationRepository.findFirstByUser_IdAndProject_IdAndLevelAndActiveIsTrue(
                        userId, projectId, RagConfigurationLevel.PROJECT))
                .thenReturn(Optional.of(cfg));

        assertThat(adapter.loadProject(userId, projectId)).hasValue(values);
    }
}
