package com.uniovi.rag.application.service.admin;

import com.uniovi.rag.infrastructure.persistence.DefaultSystemConfigurationRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.DefaultSystemConfigurationEntityFactory;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSystemDefaultsServiceTest {

    @Mock
    private DefaultSystemConfigurationRepository defaultSystemConfigurationRepository;

    @InjectMocks
    private AdminSystemDefaultsService service;

    @Test
    void getDefaults_returnsEmptyMap_whenNoRow() {
        when(defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThat(service.getDefaults()).isEmpty();
    }

    @Test
    void getDefaults_returnsValues_whenPresent() {
        DefaultSystemConfigurationEntity e = DefaultSystemConfigurationEntityFactory.emptyRow();
        e.setValues(Map.of("topK", 3));
        when(defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(e));

        assertThat(service.getDefaults()).containsEntry("topK", 3);
    }

    @Test
    void getDefaults_treatsNullValuesAsEmpty() {
        DefaultSystemConfigurationEntity e = DefaultSystemConfigurationEntityFactory.emptyRow();
        e.setValues(null);
        when(defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(e));

        assertThat(service.getDefaults()).isEmpty();
    }

    @Test
    void putDefaults_updatesExistingRow_filtersUnknownKeys_andReturnsLatest() {
        DefaultSystemConfigurationEntity e = DefaultSystemConfigurationEntityFactory.emptyRow();
        e.setValues(Map.of("topK", 1));
        when(defaultSystemConfigurationRepository.findFirstByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(e))
                .thenReturn(Optional.of(e));
        when(defaultSystemConfigurationRepository.save(any(DefaultSystemConfigurationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> out = service.putDefaults(Map.of("topK", 12, "unknownKey", "ignored"));

        verify(defaultSystemConfigurationRepository).save(Mockito.any(DefaultSystemConfigurationEntity.class));
        assertThat(out).containsEntry("topK", 12);
    }
}
